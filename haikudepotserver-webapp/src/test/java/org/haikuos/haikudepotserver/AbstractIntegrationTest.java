/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AuthenticationHelper;
import org.haikuos.haikudepotserver.security.AuthenticationService;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.db.migration.ManagedDatabase;
import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

/**
 * <p>This superclass of all of the tests has a hook to run before each integration test.  The hook will
 * basically delete all of the schema objects and then prompt the database schema migration to again,
 * repopulate the database afresh.  This ensures that the database is taken from a blank state at the
 * start of each test.  This is important for tests to maintain their independence.  The use a
 * 'transaction' over the test is not possible here as the ORM technology isnot bound to a single
 * transaction.</p>
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test.xml"
})
public abstract class AbstractIntegrationTest {

    protected static Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    private final static String DATABASEPRODUCTNAME_POSTGRES = "PostgreSQL";

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    protected ServerRuntime serverRuntime;

    @Resource
    protected AuthenticationService authenticationService;

    @Resource
    protected IntegrationTestSupportService integrationTestSupportService;

    @Resource
    protected CapturingMailSender mailSender;

    protected byte[] getResourceData(String path) throws IOException {
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream(path);

            if(null==inputStream) {
                throw new IllegalStateException("unable to find the test resource; "+path);
            }

            return ByteStreams.toByteArray(inputStream);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    private String getDatabaseName(Connection connection) throws SQLException {
        Preconditions.checkNotNull(connection);

        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            preparedStatement = connection.prepareStatement("SELECT current_database()");
            resultSet = preparedStatement.executeQuery();

            if(!resultSet.next()) {
                throw new IllegalStateException("unable to get the current database name");
            }

            return resultSet.getString(1);
        }
        finally {
            Closeables.closeQuietly(resultSet);
            Closeables.closeQuietly(preparedStatement);
        }
    }

    @After
    public void afterEachTest() {
        setUnauthenticated();
        mailSender.clear();
    }

    /**
     * <p>Before each test is run, we want to remove all of the database objects and then re-populate
     * them back again.</p>
     */

    @Before
    public void beforeEachTest() {

        LOGGER.info("will prepare for the next test");

        // reset the apache cayenne cache before we go behind its back and
        // clear out the database for the next test.

        serverRuntime.getDataDomain().getQueryCache().clear();
        serverRuntime.getDataDomain().getSharedSnapshotCache().clear();
        LOGGER.info("prep; have cleared out cayenne caches");

        // get all of the databases that are managed.

        Map<String,ManagedDatabase> managedDatabases = applicationContext.getBeansOfType(ManagedDatabase.class);

        for(ManagedDatabase managedDatabase : managedDatabases.values()) {

            Connection connection = null;
            PreparedStatement statement = null;

            try {
                connection = managedDatabase.getDataSource().getConnection();
                connection.setAutoCommit(false);

                String databaseProductName = connection.getMetaData().getDatabaseProductName();

                if(!databaseProductName.equals(DATABASEPRODUCTNAME_POSTGRES)) {
                    throw new IllegalStateException(String.format(
                            "the system is designed to be tested against %s database product, but is '%s'",
                            DATABASEPRODUCTNAME_POSTGRES,
                            databaseProductName));
                }

                if(!getDatabaseName(connection).endsWith("_integrationtest")) {
                    throw new IllegalStateException("unable to proceed with integration tests against a database which is not an integration test database");
                }

                {
                    String statementString = "DROP SCHEMA "+managedDatabase.getSchema()+" CASCADE";
                    statement = connection.prepareStatement(statementString);
                    statement.execute();
                }
            }
            catch(SQLException se) {
                throw new IllegalStateException("a database problem has arisen in preparing for an integration test",se);
            }
            finally {
                Closeables.closeQuietly(statement);
                Closeables.closeQuietly(connection);
            }

            managedDatabase.migrate();
            LOGGER.info("prep; did drop database objects for schema '{}' and re-create them", managedDatabase.getSchema());
        }

        mailSender.clear();

        LOGGER.info("did prepare for the next test");
    }

    protected void setAuthenticatedUser(String nickname) {
        ObjectContext objectContext = serverRuntime.getContext();
        Optional<User> rootUser = User.getByNickname(objectContext, nickname);
        AuthenticationHelper.setAuthenticatedUserObjectId(Optional.of(rootUser.get().getObjectId()));
    }

    /**
     * <p>Some tests will enforce authorization; in order to test the method's actual logic rather than the
     * authorization logic, it is sometimes handy to force the authenticated user to be root.  This method
     * will do this.</p>
     */

    protected void setAuthenticatedUserToRoot() {
        setAuthenticatedUser("root");
    }

    protected void setUnauthenticated() {
        AuthenticationHelper.setAuthenticatedUserObjectId(Optional.<ObjectId>absent());
    }

}
