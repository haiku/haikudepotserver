/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.access.DataNode;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.map.DataMap;
import org.apache.cayenne.map.DbEntity;
import org.apache.cayenne.map.ObjEntity;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.security.UserAuthentication;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.support.TestDatabase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.context.support.TestPropertySourceUtils;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>This superclass of all the tests has a hook to run before each integration test.  The hook will
 * basically delete all the schema objects and then prompt the database schema migration to again,
 * repopulate the database afresh.  This ensures that the database is taken from a blank state at the
 * start of each test.  This is important for tests to maintain their independence.  The use a
 * 'transaction' over the test is not possible here as the ORM technology is not bound to a single
 * transaction.</p>
 */

@ExtendWith(SpringExtension.class)
@ContextConfiguration(initializers = AbstractIntegrationTest.PostgresContainerInitializer.class)
public abstract class AbstractIntegrationTest {

    protected static Logger LOGGER = LoggerFactory.getLogger(AbstractIntegrationTest.class);

    protected static DataSourceProperties dataSourceProperties;

    static {
        dataSourceProperties = TestDatabase.startDatabase(TestDatabase.deriveType());
    }

    private final static Set<String> CDO_NAMES_RETAINED =
            Stream.of(User.class, UserUsageConditions.class)
                    .map(Class::getSimpleName)
                    .collect(Collectors.toSet());

    private final static String DATABASEPRODUCTNAME_POSTGRES = "PostgreSQL";

    private final static String SQL_DELETE_TEST_UUC = "DELETE FROM haikudepot.user_usage_conditions WHERE code LIKE 'TEST%'";

    private final static String SQL_TRUNCATE_JOBS = "TRUNCATE " + Stream.of(
            "job", "job_state", "job_generated_data", "job_supplied_data",
            "job_data_media_type", "job_specification", "job_type"
    ).map("job.%s"::formatted).collect(Collectors.joining(", "));

    @Resource
    protected ApplicationContext applicationContext;

    @Resource
    protected ServerRuntime serverRuntime;

    @Resource
    protected UserAuthenticationService userAuthenticationService;

    @Resource
    protected IntegrationTestSupportService integrationTestSupportService;

    @Resource
    protected CapturingMailSender mailSender;

    @Resource
    protected NaturalLanguageService naturalLanguageService;

    @Resource
    protected JobService jobService;

    protected void assertEqualsLineByLine(BufferedReader expected, BufferedReader actual) throws IOException {
        String expectedLine;
        String actualLine;
        int line = 1;

        do {
            expectedLine = expected.readLine();
            actualLine = actual.readLine();

            if (!Objects.equals(expectedLine, actualLine)) {
                Assertions.fail("mismatch expected and actual; [" + expectedLine + "] [" + actualLine + "] @ line " + line);
            }

            line++;
        }
        while (null != expectedLine || null != actualLine);
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
                ResultSet resultSet = preparedStatement.executeQuery()
        ) {

            if (!resultSet.next()) {
                throw new IllegalStateException("unable to get the current database name");
            }

            return resultSet.getString(1);
        }
    }

    @AfterEach
    public void afterEachTest() {
        setUnauthenticated();
        mailSender.clear();
    }

    /**
     * <p>Before each test is run, we want to remove all of the database objects and then re-populate
     * them back again.</p>
     */

    @BeforeEach
    public void beforeEachTest() {
        LOGGER.debug("will prepare for the next test");
        clearJobs();
        clearCaches();
        clearDatabaseTables();
        clearJobDatabaseTables();
        clearTestUserUsageConditions();
        setUnauthenticated();
        mailSender.clear();
        LOGGER.debug("did prepare for the next test");
    }

    protected void clearJobs() {
        if (!jobService.awaitAllJobsFinishedUninterruptibly(Duration.ofSeconds(30).toMillis())) {
            Assertions.fail("unable to complete all jobs in timeout");
        }
    }

    protected void clearCaches() {
        serverRuntime.getDataDomain().getQueryCache().clear();
        serverRuntime.getDataDomain().getSharedSnapshotCache().clear();
        LOGGER.debug("prep; have cleared out caches");
    }

    private void clearTestUserUsageConditions() {
        DataNode dataNode = serverRuntime.getDataDomain().getDataNode("HaikuDepotServer");
        try (
                Connection connection = dataNode.getDataSource().getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(SQL_DELETE_TEST_UUC)) {
            int deleted = preparedStatement.executeUpdate();

            if (0 != deleted) {
                LOGGER.info("did delete [{}] test user usage conditions", deleted);
            }

        } catch (SQLException se) {
            throw new IllegalStateException(
                    "unable to clear the [" + UserUsageConditions.class.getSimpleName() + "] from test",
                    se);
        }
    }

    private void clearDatabaseTables() {
        for (DataNode dataNode : serverRuntime.getDataDomain().getDataNodes()) {

            LOGGER.debug("prep; will clear out data for data node; {}", dataNode.getName());

            try (Connection connection = dataNode.getDataSource().getConnection()) {

                connection.setAutoCommit(false);
                connection.rollback();

                String databaseProductName = connection.getMetaData().getDatabaseProductName();

                if (!databaseProductName.equals(DATABASEPRODUCTNAME_POSTGRES)) {
                    throw new IllegalStateException(String.format(
                            "the system is designed to be tested against %s database product, but is '%s'",
                            DATABASEPRODUCTNAME_POSTGRES,
                            databaseProductName));
                }

                if (!getDatabaseName(connection).endsWith("_integrationtest")) {
                    throw new IllegalStateException("unable to proceed with integration tests against a database which is not an integration test database");
                }

                for (DataMap dataMap : dataNode.getDataMaps()) {

                    List<String> truncationNames = new ArrayList<>();

                    for (ObjEntity objEntity : dataMap.getObjEntities()) {

                        if (!objEntity.isReadOnly() && !CDO_NAMES_RETAINED.contains(objEntity.getName())) {
                            truncationNames.add(objEntity.getDbEntity().getSchema() + "." + objEntity.getDbEntity().getName());
                        }

                    }

                    if (!truncationNames.isEmpty()) {
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
            } catch (SQLException se) {
                throw new RuntimeException("unable to clear the data for the data node; " + dataNode.getName(), se);
            }

            LOGGER.debug("prep; did clear out data for data node; {}", dataNode.getName());
        }
    }

    public void clearJobDatabaseTables() {
        DataNode dataNode = serverRuntime.getDataDomain().getDataNode("HaikuDepotServer");
        try (
                Connection connection = dataNode.getDataSource().getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(SQL_TRUNCATE_JOBS)) {
            int deleted = preparedStatement.executeUpdate();

            if (0 != deleted) {
                LOGGER.info("did delete [{}] job related objects", deleted);
            }
        } catch (SQLException se) {
            throw new IllegalStateException(
                    "unable to clear the pg job data from test",
                    se);
        }
    }

    protected void setAuthenticatedUser(String nickname) {
        ObjectId oid = User.getByNickname(serverRuntime.newContext(), nickname).getObjectId();
        Authentication authentication = new UserAuthentication(oid);
        authentication.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(authentication);
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
        SecurityContextHolder.clearContext();
    }

    /**
     * <p>This will add some config parameters into the Spring environment to connect to the
     * test databases.  If Test Containers are used then this will connect to that.  If test
     * containers are not used then it will expect that a local database is supplied via an
     * environment variable.</p>
     */

    static class PostgresContainerInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    applicationContext,
                    "spring.datasource.url=" + dataSourceProperties.getUrl(),
                    "spring.datasource.username=" + dataSourceProperties.getUsername(),
                    "spring.datasource.password=" + dataSourceProperties.getPassword()
            );
        }
    }

}
