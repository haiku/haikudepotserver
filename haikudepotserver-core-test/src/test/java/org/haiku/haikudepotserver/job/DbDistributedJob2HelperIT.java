/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Job;
import org.haiku.haikudepotserver.dataobjects.JobData;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.opentest4j.AssertionFailedError;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.stream.Stream;

// By setting the noop job service type, the job service will not interfere with
// these tests while they are running.
@TestPropertySource(
        properties = """
    hds.jobservice.type=noop
  """)
@ContextConfiguration(classes = TestConfig.class)
class DbDistributedJob2HelperIT extends AbstractIntegrationTest {

    private final static String SELECT_NEXTVAL = "SELECT NEXTVAL(?)";

    private final static String SELECT_JOB_TYPE = "SELECT jt.id FROM job2.job_type jt WHERE jt.code = ?";
    private final static String SELECT_JOB_DATA_MEDIA_TYPE = "SELECT jdmt.id FROM job2.job_data_media_type jdmt WHERE jdmt.code = ?";

    private final static String SELECT_JOB_STATUS_DATA = """
            SELECT
                j.progress_percent,
                j.fail_timestamp,
                j.cancel_timestamp,
                j.finish_timestamp,
                j.start_timestamp,
                j.queue_timestamp
            FROM job2.job j
            WHERE j.code = ?
            """;

    private final static String SELECT_SUPPLIED_JOB_DATA = """
            SELECT jd.code FROM job2.job_data jd JOIN job2.job_data_type jdt ON jdt.id = jd.job_data_type_id
                           WHERE jdt.code = 'supplied' AND jd.code = ?
            """;

    private final static String SELECT_JOB_DATA_CODES_FOR_JOB = """
            SELECT jd.code
            FROM job2.job_data jd
            JOIN job2.job j ON j.id = jd.job_id
            JOIN job2.job_data_type jdt ON jdt.id = jd.job_data_type_id
            WHERE 1 = 1 AND j.code = ? AND jdt.code = ?
            """;

    private final static String INSERT_JOB_TYPE = "INSERT INTO job2.job_type (id, code) VALUES (?, ?)";
    private final static String INSERT_JOB_DATA_MEDIA_TYPE = "INSERT INTO job2.job_data_media_type (id, code) VALUES (?, ?)";

    private final static String INSERT_JOB_DATA = """
            INSERT INTO job2.job_data (
                id,
                job_id,
                modify_timestamp,
                create_timestamp,
                code,
                use_code,
                storage_code,
                job_data_encoding_id,
                job_data_media_type_id,
                job_data_type_id
            ) VALUES (
                ?,
                (SELECT j2.id FROM job.job j2 WHERE j2.code = ?),
                ?,
                ?,
                ?,
                ?,
                ?,
                (SELECT jda.id FROM job2.job_data_encoding jda WHERE jda.code = ?),
                ?,
                (SELECT jdt.id FROM job2.job_data_type jdt WHERE jdt.code = ?)
            )
            """;

    private final static String INSERT_JOB_ASSIGNMENT = """
            INSERT INTO job2.job_assignment (id, code) VALUES (?, ?)
            """;

    private final static String INSERT_JOB = """
            INSERT INTO job2.job (
                id,
                modify_timestamp,
                create_timestamp,
                start_timestamp,
                finish_timestamp,
                queue_timestamp,
                fail_timestamp,
                cancel_timestamp,
                progress_percent,
                code,
                job_type_id,
                specification,
                expiry_timestamp,
                owner_user_nickname,
                job_assignment_id
            ) VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                (SELECT jt.id FROM job2.job_type jt WHERE jt.code = ?),
                ?,
                ?,
                ?,
                ?
            );
            """;

    @Resource
    private DataSource dataSource;

    @Test
    public void testCountNotFinished() throws SQLException {

        ObjectContext objectContext = serverRuntime.newContext();

        createAnyJob(JobSnapshot.Status.STARTED);
        createAnyJob(JobSnapshot.Status.STARTED);
        createAnyJob(JobSnapshot.Status.FAILED); // <-- not included

        // -------------------------
        long actual = DbDistributedJob2Helper.countNotFinished(objectContext);
        // -------------------------

        Assertions.assertThat(actual).isEqualTo(2);
    }

    @Test
    public void testIsFinished_positive() throws SQLException {
        ObjectContext objectContext = serverRuntime.newContext();
        String jobCode = createAnyJob(JobSnapshot.Status.FAILED);

        // -------------------------
        boolean isFinished = DbDistributedJob2Helper.isFinished(objectContext, jobCode);
        // -------------------------

        Assertions.assertThat(isFinished).isTrue();
    }

    @Test
    public void testIsFinished_negative() throws SQLException {
        ObjectContext objectContext = serverRuntime.newContext();
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);

        // -------------------------
        boolean isFinished = DbDistributedJob2Helper.isFinished(objectContext, jobCode);
        // -------------------------

        Assertions.assertThat(isFinished).isFalse();
    }

    @Test
    public void testDeleteJob() throws SQLException {
        String jobCode = createAnyJob(JobSnapshot.Status.FAILED);
        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();
        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        boolean actualWasDeleted = DbDistributedJob2Helper.deleteJob(objectContext, jobCode);
        // -------------------------

        objectContext.commitChanges();

        Assertions.assertThat(actualWasDeleted).isTrue();
        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isFalse();
    }

    @Test
    public void testSetJobProgressPercent() throws SQLException {
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);
        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        boolean changed = DbDistributedJob2Helper.setJobProgressPercent(objectContext, jobCode, 66);
        // -------------------------

        Assertions.assertThat(changed).isTrue();

        objectContext.commitChanges();

        Optional<TestJobState> jobStateOptional = tryGetTestJobStateForJob(jobCode);
        Assertions.assertThat(jobStateOptional.isPresent()).isTrue();
        Assertions.assertThat(jobStateOptional.get().status).isEqualTo(JobSnapshot.Status.STARTED);
        Assertions.assertThat(jobStateOptional.get().progressPercentage).isEqualTo(66);
    }

    /**
     * <p>In this test we do find a Job to delete.</p>
     */
    @Test
    public void testClearCompletedExpiredJobs_positive() throws SQLException {
        String jobCode = createAnyJob(JobSnapshot.Status.FINISHED);
        // ^ This one has an expiry 30 mins into the future
        Instant now = Clock.systemUTC().instant().plus(Duration.ofMinutes(60));
        // ^ the job would have expired at this point.

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();

        // -------------------------
        DbDistributedJob2Helper.clearCompletedExpiredJobs(serverRuntime, now);
        // -------------------------

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isFalse();
    }

    /**
     * <p>In this test we do NOT find a Job to delete.</p>
     */
    @Test
    public void testClearCompletedExpiredJobs_negative() throws SQLException {
        String jobCode = createAnyJob(JobSnapshot.Status.FINISHED);
        // ^ This one has an expiry 30 mins into the future.
        Instant now = Clock.systemUTC().instant();
        // ^ has not expired yet.

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();

        // -------------------------
        DbDistributedJob2Helper.clearCompletedExpiredJobs(serverRuntime, now);
        // -------------------------

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();
        // ^ not deleted
    }

    @Test
    public void testFindJobs() throws SQLException {
        Instant now = Clock.systemUTC().instant();
        String code1 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        String code2 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        String code3 = createAnyJob("orange", JobSnapshot.Status.FAILED, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));
        String code4 = createAnyJob("orange", JobSnapshot.Status.QUEUED, now.minus(Duration.ofMinutes(4)), now.minus(Duration.ofMinutes(4)));
        String code5 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)));
        String code6 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(6)));
        String code7 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(7)), now.minus(Duration.ofMinutes(7)));
        String code8 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(8)));

        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        List<Job> jobs = DbDistributedJob2Helper.findJobs(
                objectContext,
                "erik",
                Set.of(JobSnapshot.Status.STARTED),
                2,
                3
        );
        // -------------------------

        Assertions.assertThat(jobs.stream().map(Job::getCode).toList()).containsExactly(code5, code6, code7);
    }

    @Test
    public void testTotalJobs() throws SQLException {
        Instant now = Clock.systemUTC().instant();
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        createAnyJob("orange", JobSnapshot.Status.FAILED, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));
        createAnyJob("orange", JobSnapshot.Status.QUEUED, now.minus(Duration.ofMinutes(4)), now.minus(Duration.ofMinutes(4)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(6)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(7)), now.minus(Duration.ofMinutes(7)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(8)));

        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        Long count = DbDistributedJob2Helper.totalJobs(
                objectContext,
                "erik",
                Set.of(JobSnapshot.Status.STARTED)
        );
        // -------------------------

        Assertions.assertThat(count).isEqualTo(6);
    }

    @Test
    public void testCreateGeneratedJobData() throws SQLException {
        String jobDataCode = UUID.randomUUID().toString();
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);

        {
            ObjectContext objectContext = serverRuntime.newContext();

            // -------------------------
            JobData jobData = DbDistributedJob2Helper.createGeneratedJobData(
                    objectContext,
                    jobCode,
                    jobDataCode,
                    "bananas",
                    "application/pdf",
                    "none"
            );
            // -------------------------

            objectContext.commitChanges();

            Assertions.assertThat(jobData.getCode()).isEqualTo(jobDataCode);
            Assertions.assertThat(jobData.getJob().getCode()).isEqualTo(jobCode);
            Assertions.assertThat(jobData.getUseCode()).isEqualTo("bananas");
            Assertions.assertThat(jobData.getJobDataEncoding().getCode()).isEqualTo("none");
            Assertions.assertThat(jobData.getJobDataMediaType().getCode()).isEqualTo("application/pdf");
        }

        Set<String> jobDataCodes = getJobDataCodesForJobCode(jobCode, "generated");
        Assertions.assertThat(jobDataCodes).hasSize(1);
    }

    /**
     * Note that supplied data is created initially without a Job attached.
     */
    @Test
    public void testCreateSuppliedJobData() throws SQLException {
        String jobDataCode = UUID.randomUUID().toString();

        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        JobData jobData = DbDistributedJob2Helper.createSuppliedJobData(
                objectContext,
                jobDataCode,
                "bananas",
                "application/pdf",
                "none"
        );
        // -------------------------

        objectContext.commitChanges();

        Assertions.assertThat(jobData.getCode()).isEqualTo(jobDataCode);
        Assertions.assertThat(jobData.getJob()).isNull();
        Assertions.assertThat(jobData.getUseCode()).isEqualTo("bananas");
        Assertions.assertThat(jobData.getJobDataEncoding().getCode()).isEqualTo("none");
        Assertions.assertThat(jobData.getJobDataMediaType().getCode()).isEqualTo("application/pdf");

        // check that it has been inserted into the database.

        {
            try (
                    Connection connection = dataSource.getConnection();
                    PreparedStatement statement = connection.prepareStatement(SELECT_SUPPLIED_JOB_DATA)
            ) {
                connection.setAutoCommit(false);
                statement.setString(1, jobData.getCode());

                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        org.junit.jupiter.api.Assertions.fail("expected the job [" + jobData.getCode() + "] but found none");
                    }
                }
            }
        }
    }

    private static Stream<Arguments> privateTestUpdateJobStatus() {
        return Stream.of(
                Arguments.of(JobSnapshot.Status.INDETERMINATE, JobSnapshot.Status.QUEUED),
                Arguments.of(JobSnapshot.Status.QUEUED, JobSnapshot.Status.STARTED),
                Arguments.of(JobSnapshot.Status.STARTED, JobSnapshot.Status.FINISHED),
                Arguments.of(JobSnapshot.Status.STARTED, JobSnapshot.Status.FAILED),
                Arguments.of(JobSnapshot.Status.STARTED, JobSnapshot.Status.CANCELLED)
        );
    }

    /**
     * <p>This test is only covering all the legitimate transitions.</p>
     */
    @ParameterizedTest
    @MethodSource("privateTestUpdateJobStatus")
    public void testUpdateJobStatus(JobSnapshot.Status startStatus, JobSnapshot.Status endStatus) throws SQLException {
        String jobCode = createAnyJob(startStatus);
        Instant now = Clock.systemUTC().instant();
        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        DbDistributedJob2Helper.updateJobStatus(
                objectContext,
                jobCode,
                now,
                endStatus
        );
        // -------------------------

        objectContext.commitChanges();

        Optional<TestJobState> testJobState = tryGetTestJobStateForJob(jobCode);
        Assertions.assertThat(testJobState.isPresent()).isTrue();
        Assertions.assertThat(testJobState.get().status()).isEqualTo(endStatus);
    }

    @Test
    public void testStreamJobsByTypeAndStatuses() throws SQLException {
        Instant now = Clock.systemUTC().instant();
        String code1 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        String code2 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        String code3 = createAnyJob("orange", JobSnapshot.Status.FAILED, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));
        String code4 = createAnyJob("orange", JobSnapshot.Status.QUEUED, now.minus(Duration.ofMinutes(4)), now.minus(Duration.ofMinutes(4)));
        String code5 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)));
        String code6 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(6)));
        String code7 = createAnyJob("banana", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(7)), now.minus(Duration.ofMinutes(7)));
        String code8 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(8)));

        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        List<String> jobCodes = DbDistributedJob2Helper.streamJobsByTypeAndStatuses(objectContext, "orange", Set.of(JobSnapshot.Status.STARTED))
                .map(Job::getCode)
                .toList();
        // -------------------------

        // excludes 3, 4 as they are the wrong status and exclude 7 because it has the wrong type code
        Assertions.assertThat(jobCodes).containsExactly(code1, code2, code5, code6, code8);

    }

    @Test
    public void testCreateJob() throws SQLException {
        Instant now = Clock.systemUTC().instant();
        String jobCode = UUID.randomUUID().toString();
        ObjectMapper objectMapper = new ObjectMapper();

        // create some supplied data.
        String suppliedJobDataCode = createJobSuppliedData(null);
        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        DbDistributedJob2Helper.createJob(
                objectContext,
                jobCode,
                "mango",
                "ralph",
                now,
                Duration.ofMinutes(10).toMillis(),
                objectMapper.createObjectNode(),
                Set.of(suppliedJobDataCode),
                false);
        // -------------------------

        objectContext.commitChanges();

        Optional<TestJobState> testJobStateOptional = tryGetTestJobStateForJob(jobCode);

        Assertions.assertThat(testJobStateOptional.isPresent()).isTrue();
        Assertions.assertThat(testJobStateOptional.get().status()).isEqualTo(JobSnapshot.Status.QUEUED);

        // check that the supplied data is now associated with the job.

        Assertions.assertThat(getJobDataCodesForJobCode(jobCode, "supplied")).containsOnly(suppliedJobDataCode);
    }

    /**
     * <p>This will get a job to process, but while it is being processed, the job should not be found
     * by the same method on another transaction.</p>
     */

    @Test
    public void testTryGetNextAvailableJob() throws SQLException {
        String jobCode = createAnyJob(JobSnapshot.Status.QUEUED);

        try (Connection connection1 = dataSource.getConnection()) {

            connection1.setAutoCommit(false);

            // -------------------------
            Optional<String> jobOptional1 = DbDistributedJob2Helper.tryGetNextAvailableJobCode(connection1);
            // -------------------------

            Assertions.assertThat(jobOptional1.isPresent()).isTrue();
            Assertions.assertThat(jobOptional1.get()).isEqualTo(jobCode);

            // In this case the job is not marked as "started" and so it will be found again.
            // This is an intentional part of the test. It will try to acquire the lock a few
            // times and fail.

            try (Connection connection2 = dataSource.getConnection()) {

                connection2.setAutoCommit(false);

                // -------------------------
                Optional<String> jobOptional2 = DbDistributedJob2Helper.tryGetNextAvailableJobCode(connection2);
                // -------------------------

                Assertions.assertThat(jobOptional2.isPresent()).isFalse();
            }
        }
    }

    /**
     * <p>Creates a number of jobs. One of the started ones will be locked by another thread. Get
     * the danging started jobs' codes. This should not include the one that is locked by the other
     * thread because the thread will be working on the job.</p>
     */
    @Test
    public void testGetDanglingStartedJobCodes() throws SQLException {
        Instant now = Clock.systemUTC().instant();
        String code1 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        String code2 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        String code3 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        String code4 = createAnyJob("orange", JobSnapshot.Status.FAILED, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));

        Thread thread;
        Semaphore testCompleteThreadSem = new Semaphore(1);
        Semaphore rowDidLockThreadSem = new Semaphore(1);

        try {
            Assertions
                    .assertThat(Uninterruptibles.tryAcquireUninterruptibly(testCompleteThreadSem, Duration.ofSeconds(10)))
                    .isTrue();

            Assertions
                    .assertThat(Uninterruptibles.tryAcquireUninterruptibly(rowDidLockThreadSem, Duration.ofSeconds(10)))
                    .isTrue();

            thread = Thread.startVirtualThread(() -> {

                // In this thread we query the `job_assignment` table for the code that we want to db-lock. As a side-
                // effect it gets db-locked and we use Java locks to ensure that it stays locked while the test is
                // running.

                try (
                        Connection connection = dataSource.getConnection();
                        PreparedStatement statement = connection.prepareStatement(
                                "SELECT ja.code FROM job2.job_assignment ja WHERE ja.code = ? FOR UPDATE SKIP LOCKED")
                ) {
                    connection.setAutoCommit(false);

                    statement.setString(1, code2);

                    try (ResultSet resultSet = statement.executeQuery()) {
                        if (!resultSet.next()) {
                            LOGGER.warn("unable to find the job code [{}] and acquire skip lock", code2);
                            org.junit.jupiter.api.Assertions.fail("unable to acquire skip lock");
                        }

                        Assertions.assertThat(resultSet.getString(1)).isEqualTo(code2);
                    } finally {
                        // so that the test can wait until the row is locked.
                        rowDidLockThreadSem.release();
                    }

                    // keep the row lock thread active until the test is completed
                    Assertions
                            .assertThat(Uninterruptibles.tryAcquireUninterruptibly(testCompleteThreadSem, Duration.ofSeconds(10)))
                            .isTrue();

                } catch (SQLException se) {
                    throw new AssertionFailedError("unable to find the job [" + code2 + "]", se);
                }
            });

            Assertions
                    .assertThat(Uninterruptibles.tryAcquireUninterruptibly(rowDidLockThreadSem, Duration.ofSeconds(10)))
                    .isTrue();

            Set<String> actualDanglingJobCodes;

            try (Connection connection = dataSource.getConnection()) {

                connection.setAutoCommit(false);

                // -------------------------
                actualDanglingJobCodes = DbDistributedJob2Helper.getDanglingStartedJobCodes(connection);
                // -------------------------
            }

            // This does not contain code2 because it is locked and does not contain code 4 because it is failed.
            Assertions.assertThat(actualDanglingJobCodes).containsOnly(code1, code3);

        } finally {
            testCompleteThreadSem.release();
        }

        Uninterruptibles.joinUninterruptibly(thread, Duration.ofSeconds(10));
        Assertions.assertThat(thread.isAlive()).isFalse();
    }

    private Optional<TestJobState> tryGetTestJobStateForJob(String jobCode) throws SQLException {
        Function<java.sql.Timestamp, Instant> mapTimestampFn = (ts) -> Optional.ofNullable(ts)
                .map(ts1 -> Instant.ofEpochMilli(ts1.getTime()))
                .orElse(null);

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_JOB_STATUS_DATA)
        ) {
            connection.setAutoCommit(false);

            statement.setString(1, jobCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        new TestJobState(
                                Jobs.mapTimestampsToStatus(
                                        mapTimestampFn.apply(resultSet.getTimestamp(2)), // fail_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(3)), // cancel_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(4)), // finish_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(5)), // start_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(6)) // queue_timestamp
                                ),
                                resultSet.getInt(1)
                        ));
            }
        }
    }

    private long getOrCreateJobType(String jobTypeCode) throws SQLException {
        Optional<Long> idOptional = getEnumTableEntry(SELECT_JOB_TYPE, jobTypeCode);

        if (idOptional.isEmpty()) {
            return createEnumTableEntry(
                    INSERT_JOB_TYPE,
                    "job2.job_type_seq",
                    jobTypeCode);
        }

        return idOptional.get();
    }

    private long getOrCreateJobDataMediaType(String jobDataMediaTypeCode) throws SQLException {
        Optional<Long> idOptional = getEnumTableEntry(SELECT_JOB_DATA_MEDIA_TYPE, jobDataMediaTypeCode);

        if (idOptional.isEmpty()) {
            return createEnumTableEntry(
                    INSERT_JOB_DATA_MEDIA_TYPE,
                    "job2.job_data_media_type_seq",
                    jobDataMediaTypeCode);
        }

        return idOptional.get();
    }

    private Optional<Long> getEnumTableEntry(String selectSql, String code) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(selectSql)
                ) {
            connection.setAutoCommit(false);

            statement.setString(1, code);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getLong(1));
                }
            }
        }
        return Optional.empty();
    }

    private long createEnumTableEntry(String insertSql, String sequenceName, String code) throws SQLException {
        long id =  nextVal(sequenceName);

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(insertSql)
        ) {
            connection.setAutoCommit(false);

            statement.setLong(1, id);
            statement.setString(2, code);

            if (1 != statement.executeUpdate()) {
                throw new IllegalStateException(String.format("unable to insert [%s]", code));
            }

            connection.commit();
        }

        return id;
    }

    private String createJobSuppliedData(String jobCode) throws SQLException {
        String jobDataCode = UUID.randomUUID().toString();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(Clock.systemUTC().millis());

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_JOB_DATA);
        ) {
            connection.setAutoCommit(false);

            statement.setLong(1, nextVal("job2.job_data_seq"));
            statement.setString(2, jobCode);
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            statement.setString(5, jobDataCode);
            statement.setString(6, "dunno");
            statement.setString(7, jobDataCode);
            statement.setString(8, "none");
            statement.setLong(9, getOrCreateJobDataMediaType("application/octet-stream"));
            statement.setString(10, "supplied");

            if (1 != statement.executeUpdate()) {
                throw new IllegalStateException("failed to insert job data");
            }

            connection.commit();
        }

        return jobDataCode;
    }

    private String createJobGeneratedData(String jobCode) throws SQLException {
        String jobDataCode = UUID.randomUUID().toString();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(Clock.systemUTC().millis());

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_JOB_DATA);
        ) {
            connection.setAutoCommit(false);

            statement.setLong(1, nextVal("job2.job_data_seq"));
            statement.setString(2, jobCode);
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            statement.setString(5, jobDataCode);
            statement.setString(6, "dunno");
            statement.setString(7, jobDataCode);
            statement.setString(8, "none");
            statement.setLong(9, getOrCreateJobDataMediaType("application/octet-stream"));
            statement.setString(10, "generated");

            if (1 != statement.executeUpdate()) {
                throw new IllegalStateException("failed to insert job data");
            }

            connection.commit();
        }

        return jobDataCode;
    }

    private String createAnyJob(JobSnapshot.Status jobStatus) throws SQLException {
        return createAnyJob("orange", jobStatus, Clock.systemUTC().instant(), Clock.systemUTC().instant());
    }

    private String createAnyJob(String jobTypeCode, JobSnapshot.Status jobStatus, Instant now, Instant queueTimestamp) throws SQLException {
        getOrCreateJobType(jobTypeCode);

        long jobAssignmentId = nextVal("job2.job_assignment_seq");
        long jobId = nextVal("job2.job_seq");
        java.sql.Timestamp timestamp = new java.sql.Timestamp(now.toEpochMilli());
        java.sql.Timestamp expiryTimestamp = new java.sql.Timestamp(now.plus(Duration.ofMinutes(30)).toEpochMilli());
        String jobCode = UUID.randomUUID().toString();

        Instant failTimestamp = null;
        Instant cancelTimestamp = null;
        Instant finishTimestamp = null;
        Instant startTimestamp = null;

        switch (jobStatus) {
            case QUEUED:
                if (null ==queueTimestamp) {
                    queueTimestamp = now;
                }
                break;
            case STARTED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                startTimestamp = now;
                break;
            case FINISHED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                startTimestamp = now;
                finishTimestamp = now;
                break;
            case FAILED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                startTimestamp = now;
                failTimestamp = now;
                break;
            case CANCELLED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                cancelTimestamp = now;
                break;
            case INDETERMINATE:
                break;
        }

        Function<Instant, java.sql.Timestamp> toTimestampFn = (i) ->
                Optional.ofNullable(i).map(Instant::toEpochMilli).map(java.sql.Timestamp::new).orElse(null);

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statementJobAssignment = connection.prepareStatement(INSERT_JOB_ASSIGNMENT);
                PreparedStatement statementJob = connection.prepareStatement(INSERT_JOB)
        ) {
            connection.setAutoCommit(false);

            statementJobAssignment.setLong(1, jobAssignmentId);
            statementJobAssignment.setString(2, jobCode);

            if (1 != statementJobAssignment.executeUpdate()) {
                throw new IllegalStateException("failed to insert job assignment");
            }

            statementJob.setLong(1, jobId);
            statementJob.setTimestamp(2, timestamp);
            statementJob.setTimestamp(3, timestamp);
            statementJob.setTimestamp(4, toTimestampFn.apply(startTimestamp));
            statementJob.setTimestamp(5, toTimestampFn.apply(finishTimestamp));
            statementJob.setTimestamp(6, toTimestampFn.apply(queueTimestamp));
            statementJob.setTimestamp(7, toTimestampFn.apply(failTimestamp));
            statementJob.setTimestamp(8, toTimestampFn.apply(cancelTimestamp));
            statementJob.setInt(9, 50);
            statementJob.setString(10, jobCode);
            statementJob.setString(11, jobTypeCode);
            statementJob.setString(12, "{}");
            statementJob.setTimestamp(13, expiryTimestamp);
            statementJob.setString(14, "erik");
            statementJob.setLong(15, jobAssignmentId);

            if (1 != statementJob.executeUpdate()) {
                throw new IllegalStateException("failed to insert job");
            }

            connection.commit();
        }

        return jobCode;
    }

    private long nextVal(String sequenceName) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_NEXTVAL)
        ) {
            connection.setAutoCommit(false);

            statement.setString(1, sequenceName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
            throw new IllegalStateException(String.format("cannot get the next in sequence [%s]", sequenceName));
        }
    }

    private Set<String> getJobDataCodesForJobCode(String jobCode, String jobDataTypeCode) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(SELECT_JOB_DATA_CODES_FOR_JOB);
        ) {
            connection.setAutoCommit(false);

            Set<String> result = new HashSet<>();

            preparedStatement.setString(1, jobCode);
            preparedStatement.setString(2, jobDataTypeCode);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while(resultSet.next()) {
                    result.add(resultSet.getString(1));
                }
            }

            return Collections.unmodifiableSet(result);
        }
    }

    record TestJobState (JobSnapshot.Status status, Integer progressPercentage) {}

}
