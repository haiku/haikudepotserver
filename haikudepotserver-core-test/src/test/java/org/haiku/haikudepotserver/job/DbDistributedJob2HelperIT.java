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
import org.haiku.haikudepotserver.job.model.JobFindRequest;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.junit.jupiter.api.BeforeEach;
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
import java.util.stream.Stream;

// By setting the noop job service type, the job service will not interfere with
// these tests while they are running.
@TestPropertySource(
        properties = """
                  hds.jobservice.type=noop
                """)
@ContextConfiguration(classes = TestConfig.class)
class DbDistributedJob2HelperIT extends AbstractIntegrationTest {

    @Resource
    private DataSource dataSource;

    @BeforeEach
    public void setup() {
        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "erik", "MalenMitPinsel");
    }

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

        Optional<PgJobTestHelper.TestJobState> jobStateOptional = tryGetTestJobStateForJob(jobCode);
        Assertions.assertThat(jobStateOptional.isPresent()).isTrue();
        Assertions.assertThat(jobStateOptional.get().status()).isEqualTo(JobSnapshot.Status.STARTED);
        Assertions.assertThat(jobStateOptional.get().progressPercentage()).isEqualTo(66);
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
                new JobFindRequest("erik", null, Set.of(JobSnapshot.Status.STARTED)),
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
                new JobFindRequest("erik", null, Set.of(JobSnapshot.Status.STARTED))
        );
        // -------------------------

        Assertions.assertThat(count).isEqualTo(6);
    }

    /**
     * <p>Check to make sure when the request specifies specific job types then the logic
     * returns only that sort of Job.</p>
     */

    @Test
    public void testFindJobs_jobTypeCode() throws SQLException {
        Instant now = Clock.systemUTC().instant();
        String code1 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        String code2 = createAnyJob("pear", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));

        ObjectContext objectContext = serverRuntime.newContext();

        // -------------------------
        List<Job> jobs = DbDistributedJob2Helper.findJobs(
                objectContext,
                new JobFindRequest(
                        null,
                        "pear",
                        null
                ),
                0,
                1000
        );
        // -------------------------

        Assertions.assertThat(jobs.stream().map(Job::getCode).toList()).containsExactly(code2);
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
        Assertions.assertThat(hasJobSuppliedData(jobData.getCode())).isTrue();
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

        Optional<PgJobTestHelper.TestJobState> testJobState = tryGetTestJobStateForJob(jobCode);
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
        List<String> jobCodes = DbDistributedJob2Helper.streamJobsByTypeAndStatuses(objectContext, Instant.now(), "orange", Set.of(JobSnapshot.Status.STARTED))
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
                Map.of(
                        "SOME_TAG_1", "SOME_VALUE_1",
                        "SOME_TAG_2", "SOME_VALUE_2"
                ),
                false);
        // -------------------------

        objectContext.commitChanges();

        Optional<PgJobTestHelper.TestJobState> testJobStateOptional = tryGetTestJobStateForJob(jobCode);

        Assertions.assertThat(testJobStateOptional.isPresent()).isTrue();
        Assertions.assertThat(testJobStateOptional.get().status()).isEqualTo(JobSnapshot.Status.QUEUED);

        // check that the supplied data is now associated with the job.

        Assertions.assertThat(getJobDataCodesForJobCode(jobCode, "supplied")).containsOnly(suppliedJobDataCode);

        // check that the job's tags are there.
        Map<String, String> actualTags = getJobTagsForJobCode(jobCode);
        Assertions.assertThat(actualTags).isEqualTo(Map.of(
                "SOME_TAG_1", "SOME_VALUE_1",
                "SOME_TAG_2", "SOME_VALUE_2"
        ));
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

    public boolean hasJobSuppliedData(String jobDataCode) throws SQLException {
        return PgJobTestHelper.hasJobSuppliedData(dataSource, jobDataCode);
    }

    private Optional<PgJobTestHelper.TestJobState> tryGetTestJobStateForJob(String jobCode) throws SQLException {
        return PgJobTestHelper.tryGetTestJobStateForJob(dataSource, jobCode);
    }

    private String createJobSuppliedData(String jobCode) throws SQLException {
        return PgJobTestHelper.createJobSuppliedData(dataSource, jobCode);
    }

    private String createAnyJob(JobSnapshot.Status jobStatus) throws SQLException {
        return createAnyJob("orange", jobStatus, Clock.systemUTC().instant(), Clock.systemUTC().instant());
    }

    private String createAnyJob(String jobTypeCode, JobSnapshot.Status jobStatus, Instant now, Instant queueTimestamp) throws SQLException {
        return PgJobTestHelper.createAnyJob(dataSource, jobTypeCode, jobStatus, now, queueTimestamp);
    }

    private Set<String> getJobDataCodesForJobCode(String jobCode, String jobDataTypeCode) throws SQLException {
        return PgJobTestHelper.getJobDataCodesForJobCode(dataSource, jobCode, jobDataTypeCode);
    }

    private Map<String, String> getJobTagsForJobCode(String jobCode) throws SQLException{
        return PgJobTestHelper.getJobTagsForJobCode(dataSource, jobCode);
    }

}