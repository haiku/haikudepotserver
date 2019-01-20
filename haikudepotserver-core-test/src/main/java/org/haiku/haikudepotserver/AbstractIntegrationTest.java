/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import org.apache.cayenne.access.DataNode;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.map.DataMap;
import org.apache.cayenne.map.DbEntity;
import org.apache.cayenne.map.ObjEntity;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.security.AuthenticationHelper;
import org.haiku.haikudepotserver.security.model.AuthenticationService;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <p>This superclass of all of the tests has a hook to run before each integration test.  The hook will
 * basically delete all of the schema objects and then prompt the database schema migration to again,
 * repopulate the database afresh.  This ensures that the database is taken from a blank state at the
 * start of each test.  This is important for tests to maintain their independence.  The use a
 * 'transaction' over the test is not possible here as the ORM technology is not bound to a single
 * transaction.</p>
 */

@RunWith(SpringJUnit4ClassRunner.class)
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

    @Resource
    protected NaturalLanguageService naturalLanguageService;

    protected void assertEqualsLineByLine(BufferedReader expected, BufferedReader actual) throws IOException {
            String expectedLine;
            String actualLine;
            int line = 1;

            do {
                expectedLine = expected.readLine();
                actualLine = actual.readLine();

                if(!Objects.equals(expectedLine, actualLine)) {
                    Assert.fail("mismatch expected and actual; [" + expectedLine + "] [" + actualLine + "] @ line " + line);
                }

                line++;
            }
            while(null!=expectedLine || null!=actualLine);
    }

    protected ByteSource getResourceByteSource(final String path) {
        return Resources.asByteSource(Resources.getResource(path));
    }

    protected byte[] getResourceData(String path) throws IOException {
        return getResourceByteSource(path).read();
    }

    private String getDatabaseName(Connection connection) throws SQLException {
        Preconditions.checkNotNull(connection);

        try (
                PreparedStatement preparedStatement = connection.prepareStatement("SELECT current_database()");
                ResultSet resultSet = preparedStatement.executeQuery();
        ) {

            if(!resultSet.next()) {
                throw new IllegalStateException("unable to get the current database name");
            }

            return resultSet.getString(1);
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

        LOGGER.debug("will prepare for the next test");

        serverRuntime.getDataDomain().getQueryCache().clear();
        serverRuntime.getDataDomain().getSharedSnapshotCache().clear();
        LOGGER.debug("prep; have cleared out caches");

        for(DataNode dataNode : serverRuntime.getDataDomain().getDataNodes()) {

            LOGGER.debug("prep; will clear out data for data node; {}", dataNode.getName());

            try ( Connection connection = dataNode.getDataSource().getConnection() ) {

                connection.setAutoCommit(false);
                connection.rollback();

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

                for (DataMap dataMap : dataNode.getDataMaps()) {

                    List<String> truncationNames = new ArrayList<>();

                    for (ObjEntity objEntity : dataMap.getObjEntities()) {

                        if(!objEntity.isReadOnly() && !objEntity.getName().equals(User.class.getSimpleName())) {
                            truncationNames.add(objEntity.getDbEntity().getSchema() + "." + objEntity.getDbEntity().getName());
                        }

                    }

                    if(!truncationNames.isEmpty()) {
                        String sql = String.format(
                                "TRUNCATE %s CASCADE",
                                String.join(",", truncationNames));

                        try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                            preparedStatement.execute();
                        }
                    }

                }

                // special case for the root user because we want to leave the root user in-situ

                {
                    DbEntity userDbEntity = serverRuntime.getDataDomain().getEntityResolver().getObjEntity(User.class.getSimpleName()).getDbEntity();
                    String sql = String.format("DELETE FROM %s.%s WHERE nickname <> 'root'", userDbEntity.getSchema(), userDbEntity.getName());

                    try (PreparedStatement preparedStatement = connection.prepareStatement(sql)) {
                        preparedStatement.execute();
                    }
                }


                connection.commit();
            }
            catch(SQLException se) {
                throw new RuntimeException("unable to clear the data for the data node; " + dataNode.getName(), se);
            }

            LOGGER.debug("prep; did clear out data for data node; {}", dataNode.getName());
        }

        setUnauthenticated();
        mailSender.clear();

        LOGGER.debug("did prepare for the next test");
    }

    protected void setAuthenticatedUser(String nickname) {
        AuthenticationHelper.setAuthenticatedUserObjectId(User.getByNickname(
                serverRuntime.newContext(),
                nickname).getObjectId());
    }

    /**
     * <p>Some tests will enforce authorization; in order to test the method's actual logic rather than the
     * authorization logic, it is sometimes handy to force the authenticated user to be root.  This method
     * will do this.</p>
     */

    protected void setAuthenticatedUserToRoot() {
        setAuthenticatedUser("root");
    }

    private void setUnauthenticated() {
        AuthenticationHelper.setAuthenticatedUserObjectId(null);
    }

}
