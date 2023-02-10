/*
 * Copyright 2022-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.util.FileSystemUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.shaded.org.awaitility.Awaitility;
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * <p>This class has the purpose of setting up the Postgres database that would
 * be used for supporting the integration tests.</p>
 */

public class TestDatabase {

    protected static Logger LOGGER = LoggerFactory.getLogger(TestDatabase.class);

    private static final Pattern PATTERN_POSTGRES_MAJOR_VERSION = Pattern.compile("^[0-9]+$");

    private static final String ENV_TYPE_KEY = "TEST_DATABASE_TYPE";

    private static final String DEFAULT_DATABASE = "haikudepotserver_integrationtest";
    private static final String DEFAULT_USERNAME = "haikudepotserver_integrationtest";
    private static final String DEFAULT_PASSWORD = "haikudepotserver_integrationtest";

    private static final String CMD_SU = "/bin/su";

    private static final String ROOT_POSTGRES = "/usr/lib/postgresql";
    private static final String ROOT_TEMP = "/tmp";

    private static PostgreSQLContainer<?> POSTGRES_SQL_CONTAINER;

    private static LocalPostgresEnvironment localTestDatabaseLocalPostgresEnvironment;
    private static Process localTestDatabaseProcess;

    /**
     * <p>The type defines the way that the test rig will access a database for running
     * the integration tests.</p>
     */

    public enum Type {
        START_TEST_CONTAINERS,
        START_LOCAL_DATABASE,
        LOCAL_DATABASE
    }

    public static Type deriveType() {
        return Optional.ofNullable(System.getenv(ENV_TYPE_KEY))
                .map(StringUtils::trimToNull)
                .map(Type::valueOf)
                .orElse(Type.START_TEST_CONTAINERS);
    }

    public static DataSourceProperties startDatabase(Type type) {
        try {
            switch (type) {
                case LOCAL_DATABASE -> {
                    LOGGER.info("am using a postgres database running locally");
                    DataSourceProperties properties = new DataSourceProperties();
                    properties.setUrl("jdbc:postgresql://localhost:5432/" + DEFAULT_DATABASE);
                    properties.setUsername(DEFAULT_USERNAME);
                    properties.setPassword(DEFAULT_PASSWORD);
                    return properties;
                }
                case START_TEST_CONTAINERS -> {
                    if (null == POSTGRES_SQL_CONTAINER) {
                        POSTGRES_SQL_CONTAINER =
                                new PostgreSQLContainer<>(DockerImageName.parse("postgres").withTag("14.2"))
                                        .withDatabaseName(DEFAULT_DATABASE)
                                        .withUsername(DEFAULT_USERNAME)
                                        .withPassword(DEFAULT_PASSWORD);
                        POSTGRES_SQL_CONTAINER.start();
                        LOGGER.info("am using a postgres database running in a container");
                    }
                    DataSourceProperties properties = new DataSourceProperties();
                    properties.setUrl(POSTGRES_SQL_CONTAINER.getJdbcUrl());
                    properties.setUsername(POSTGRES_SQL_CONTAINER.getUsername());
                    properties.setPassword(POSTGRES_SQL_CONTAINER.getPassword());
                    return properties;
                }
                case START_LOCAL_DATABASE -> {
                    return startLocalDatabase();
                }
                default -> throw new IllegalStateException("unanticipated test database [" + type + "]");
            }
        }
        catch (Throwable th) {
            // extra logging because the class-creation issue masks the exception
            // owing to the static logic in the test rig.
            th.printStackTrace();
            throw new RuntimeException("unable to start the test database", th);
        }
    }

    private static DataSourceProperties startLocalDatabase() throws IOException {
        Preconditions.checkState(null == localTestDatabaseLocalPostgresEnvironment);
        Preconditions.checkState(new File(ROOT_TEMP).exists(), "the temporary directory does not exist");

        Integer bestPostgresMajorVersion = tryGetBestInstalledPostgresMajorVersion()
                .orElseThrow(() -> new IllegalStateException("unable to find an installed postgres version"));

        try {
            localTestDatabaseLocalPostgresEnvironment = new LocalPostgresEnvironment(UUID.randomUUID(), bestPostgresMajorVersion);

            String initdbCommand = String.join(" ", localTestDatabaseLocalPostgresEnvironment.getCmdInitdb(),
                    "-D", localTestDatabaseLocalPostgresEnvironment.getDataDir().resolve("pgdata").toString(),
                    "--auth-local", "peer", "--auth-host", "md5");
            LOGGER.info("cmd [{}]", initdbCommand);
            Process initDbProcess = new ProcessBuilder(
                    CMD_SU, "-", "postgres", "-c", initdbCommand)
                    .redirectErrorStream(true)
                    .redirectOutput(localTestDatabaseLocalPostgresEnvironment.getLogInitdb().toFile())
                    .start();

            initDbProcess.waitFor(60, TimeUnit.SECONDS);

            if (0 != initDbProcess.exitValue()) {
                throw new RuntimeException("unable to `initdb` the test database; return code [" + initDbProcess.exitValue() + ']');
            }

            // this one is left running as a child process.
            String postgresCommand = String.join(" ", localTestDatabaseLocalPostgresEnvironment.getCmdPostgres(),
                    "-D", localTestDatabaseLocalPostgresEnvironment.getDataDir().resolve("pgdata").toString());
            LOGGER.info("cmd [{}]", postgresCommand);
            localTestDatabaseProcess = new ProcessBuilder(CMD_SU, "-", "postgres", "-c", postgresCommand)
                    .redirectErrorStream(true)
                    .redirectOutput(localTestDatabaseLocalPostgresEnvironment.getLogPostgres().toFile())
                    .start();

            // give it a sec and check that it is running.
            Thread.sleep(3000, 0);
            if (!localTestDatabaseProcess.isAlive()) {
                throw new RuntimeException("it seems like the database has failed to start");
            }

            LOGGER.info("did start the integration test database");

            // tear down the database once the build process has completed
            Runtime.getRuntime().addShutdownHook(new Thread(
                    () -> {
                        Preconditions.checkState(null != localTestDatabaseLocalPostgresEnvironment);
                        Preconditions.checkState(null != localTestDatabaseProcess);
                        try {
                            localTestDatabaseProcess.destroy();
                            LOGGER.info("did request the integration test database terminate");

                            Awaitility.with()
                                    .atMost(1, TimeUnit.MINUTES)
                                    .pollDelay(2, TimeUnit.SECONDS)
                                    .until(() -> !localTestDatabaseProcess.isAlive());
                            LOGGER.info("integration test database did terminate");

                            FileSystemUtils.deleteRecursively(localTestDatabaseLocalPostgresEnvironment.getDataDir());
                            LOGGER.info("did clean up integration test database data");
                        }
                        catch (Throwable th) {
                            System.err.println("issues arising terminating the integration test database;\n" + th);
                        }
                    })
            );

            LOGGER.info("added shutdown hook for the integration test database");

            // keep trying to run some SQL into the database to initialise it for integration testing.
            Awaitility.with()
                    .atMost(1, TimeUnit.MINUTES)
                    .pollDelay(2, TimeUnit.SECONDS)
                    .until(TestDatabase::attemptSetupDatabaseForIntegrationTests);

            LOGGER.info("did setup the integration test database");

            DataSourceProperties properties = new DataSourceProperties();
            properties.setUrl("jdbc:postgresql://localhost:5432/" + DEFAULT_DATABASE);
            properties.setUsername(DEFAULT_USERNAME);
            properties.setPassword(DEFAULT_PASSWORD);
            return properties;
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("unable to start the test database", ie);
        }
    }

    private static Optional<Integer> tryGetBestInstalledPostgresMajorVersion() {
        String[] leaves = new File(ROOT_POSTGRES).list((dir, name) -> PATTERN_POSTGRES_MAJOR_VERSION.matcher(name).matches());
        return Stream.of(leaves)
                .map(Integer::parseInt)
                .sorted()
                .max(Integer::compare);
    }

    private static boolean attemptSetupDatabaseForIntegrationTests() {
        Path sqlFile = null;

        try {
            sqlFile = Files.createTempFile("setupintegrationtest", ".sql");

            try (InputStream inputStream = TestDatabase.class.getResourceAsStream("/setup-integration-test-db.sql")) {
                if (null == inputStream) {
                    throw new IllegalStateException("unable to open the setup sql file");
                }

                Files.copy(inputStream, sqlFile, StandardCopyOption.REPLACE_EXISTING);

                String psqlCommand = String.join(" ", localTestDatabaseLocalPostgresEnvironment.getCmdPsql(),
                        "-f", sqlFile.toString());
                LOGGER.info("cmd [{}]", psqlCommand);
                Process setupTestDatabaseProcess = new ProcessBuilder(
                        CMD_SU, "-", "postgres", "-c", psqlCommand)
                        .redirectErrorStream(true)
                        .redirectOutput(localTestDatabaseLocalPostgresEnvironment.getLogPsql().toFile())
                        .start();

                setupTestDatabaseProcess.waitFor(30, TimeUnit.SECONDS);

                if (0 == setupTestDatabaseProcess.exitValue()) {
                    LOGGER.info("did setup the database for integration testing");
                    return true;
                }

                LOGGER.error("failed to setup the database for integration testing");
                return false;
            }
        }
        catch (IOException ioe) {
            throw new UncheckedIOException("unable to setup for integration tests", ioe);
        }
        catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("unable to start the test database", ie);
        }
        finally {
            if (null != sqlFile) {
                if (!sqlFile.toFile().delete()) {
                    LOGGER.error("unable to delete the temporary sql file for setup for integration test");
                }
            }
        }
    }

    /**
     * <p>This models the Postgres environment to capture the details required for
     * launching the Postgres database locally that will be used for integration
     * testing.</p>
     */

    public static class LocalPostgresEnvironment {

        private final UUID uuid;
        private final Integer majorVersion;

        public LocalPostgresEnvironment(UUID uuid, Integer majorVersion) {
            this.uuid = Preconditions.checkNotNull(uuid);
            this.majorVersion = Preconditions.checkNotNull(majorVersion);
        }

        public Path getDataDir() {
            return Path.of(ROOT_TEMP, uuid + "-hdstest-pgdata");
        }

        public String getCmdInitdb() {
            return Path.of(ROOT_POSTGRES, majorVersion.toString(), "bin", "initdb").toString();
        }

        public String getCmdPostgres() {
            return Path.of(ROOT_POSTGRES, majorVersion.toString(), "bin", "postgres").toString();
        }

        public String getCmdPsql() {
            return Path.of(ROOT_POSTGRES, majorVersion.toString(), "bin", "psql").toString();
        }

        public Path getLogInitdb() {
            return Path.of(ROOT_TEMP, uuid + "-initdb.log");
        }

        public Path getLogPostgres() {
            return Path.of(ROOT_TEMP, uuid + "-postgres.log");
        }

        public Path getLogPsql() {
            return Path.of(ROOT_TEMP, uuid + "-psql.log");
        }
    }

}
