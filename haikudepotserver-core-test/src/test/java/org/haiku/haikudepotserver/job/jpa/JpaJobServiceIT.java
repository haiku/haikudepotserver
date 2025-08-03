/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.Jobs;
import org.haiku.haikudepotserver.job.jpa.model.Job;
import org.haiku.haikudepotserver.job.jpa.model.JobGeneratedData;
import org.haiku.haikudepotserver.job.jpa.model.JobSuppliedData;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
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
class JpaJobServiceIT extends AbstractIntegrationTest {

    private final static String SELECT_NEXTVAL = "SELECT NEXTVAL(?)";

    private final static String SELECT_JOB_TYPE = "SELECT jt.id FROM job.job_type jt WHERE jt.code = ?";
    private final static String SELECT_JOB_DATA_MEDIA_TYPE = "SELECT jdmt.id FROM job.job_data_media_type jdmt WHERE jdmt.code = ?";

    private final static String SELECT_JOB_STATE_STATUS_CODE_FOR_JOB = """
            SELECT
                js1.progress_percent,
                js1.fail_timestamp,
                js1.cancel_timestamp,
                js1.finish_timestamp,
                js1.start_timestamp,
                js1.queue_timestamp
            FROM job.job j
            JOIN job.job_state js1 ON js1.id = j.job_state_id
            WHERE j.code = ?
            """;

    private final static String SELECT_JOB_STATE_ID_FOR_JOB_CODE = """
            SELECT js.id FROM job.job_state js JOIN job.job j ON j.job_state_id = js.id WHERE j.code = ?
            """;

    private final static String SELECT_GENERATED_JOB_DATA = """
            SELECT jd.code FROM job.job_generated_data jd WHERE code = ?
            """;

    private final static String SELECT_SUPPLIED_JOB_DATA = """
            SELECT jd.code FROM job.job_supplied_data jd WHERE code = ?
            """;

    private final static String SELECT_JOB_SUPPLIED_DATA_CODES_FOR_JOB = """
            SELECT jd.code
            FROM job.job_supplied_data jd JOIN job.job j ON j.id = jd.job_id
            WHERE j.code = ?
            """;

    private final static String INSERT_JOB_TYPE = "INSERT INTO job.job_type (id, code) VALUES (?, ?)";
    private final static String INSERT_JOB_DATA_MEDIA_TYPE = "INSERT INTO job.job_data_media_type (id, code) VALUES (?, ?)";

    private final static String INSERT_JOB_GENERATED_DATA = """
            INSERT INTO job.job_generated_data (
                id,
                job_state_id,
                modify_timestamp,
                create_timestamp,
                code,
                use_code,
                storage_code,
                job_data_encoding_id,
                job_data_media_type_id
            ) VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                (SELECT jda.id FROM job.job_data_encoding jda WHERE jda.code = ?),
                ?
            )
            """;

    private final static String INSERT_JOB_SUPPLIED_DATA = """
            INSERT INTO job.job_supplied_data (
                id,
                job_id,
                modify_timestamp,
                create_timestamp,
                code,
                use_code,
                storage_code,
                job_data_encoding_id,
                job_data_media_type_id
            ) VALUES (
                ?,
                (SELECT j2.id FROM job.job j2 WHERE j2.code = ?),
                ?,
                ?,
                ?,
                ?,
                ?,
                (SELECT jda.id FROM job.job_data_encoding jda WHERE jda.code = ?),
                ?
            )
            """;

    private final static String INSERT_JOB_SPECIFICATION = """
           INSERT INTO job.job_specification (
                id,
                modify_timestamp,
                create_timestamp,
                data
           ) VALUES (
                ?,
                ?,
                ?,
                to_json(?)
           )
           """;

    private final static String INSERT_JOB_STATE = """
            INSERT INTO job.job_state (
                id,
                modify_timestamp,
                create_timestamp,
                start_timestamp,
                finish_timestamp,
                queue_timestamp,
                fail_timestamp,
                cancel_timestamp,
                progress_percent
            ) VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?
            )
            """;

    private final static String INSERT_JOB = """
            INSERT INTO job.job (
                id,
                modify_timestamp,
                create_timestamp,
                code,
                job_type_id,
                job_state_id,
                job_specification_id,
                expiry_timestamp,
                owner_user_nickname
            ) VALUES (
                ?,
                ?,
                ?,
                ?,
                (SELECT jt.id FROM job.job_type jt WHERE jt.code = ?),
                      ?,
                      ?,
                ?,
                ?
            );
            """;

    @Resource
    private JpaJobService service;

    @Resource
    private JdbcTemplate jdbcTemplate;

    @Resource
    private PlatformTransactionManager transactionManager;

    private TransactionTemplate transactionTemplate;

    @PostConstruct
    public void setup() {
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    public void testCountNotFinished() {

        createAnyJob(JobSnapshot.Status.STARTED);
        createAnyJob(JobSnapshot.Status.STARTED);
        createAnyJob(JobSnapshot.Status.FAILED); // <-- not included

        // -------------------------
        long actual = service.countNotFinished();
        // -------------------------

        Assertions.assertThat(actual).isEqualTo(2);
    }

    @Test
    public void testIsFinished_positive() {
        String jobCode = createAnyJob(JobSnapshot.Status.FAILED);

        // -------------------------
        boolean isFinished = service.isFinished(jobCode);
        // -------------------------

        Assertions.assertThat(isFinished).isTrue();
    }

    @Test
    public void testIsFinished_negative() {
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);

        // -------------------------
        boolean isFinished = service.isFinished(jobCode);
        // -------------------------

        Assertions.assertThat(isFinished).isFalse();
    }

    @Test
    public void testExistsJob_positive() {
        String jobCode = createAnyJob(JobSnapshot.Status.FAILED);

        // -------------------------
        boolean exists = service.existsjob(jobCode);
        // -------------------------

        Assertions.assertThat(exists).isTrue();
    }

    @Test
    public void testExistsJob_negative() {

        // -------------------------
        boolean exists = service.existsjob(UUID.randomUUID().toString());
        // -------------------------

        Assertions.assertThat(exists).isFalse();
    }

    @Test
    public void testTryGetJob_positive() {
        String jobCode = createAnyJob(JobSnapshot.Status.FAILED);

        // -------------------------
        Optional<Job> jobOptional = service.tryGetJob(jobCode);
        // -------------------------

        Assertions.assertThat(jobOptional.isPresent()).isTrue();
        Job job = jobOptional.get();

        Assertions.assertThat(job.getCode()).isEqualTo(jobCode);
        Assertions.assertThat(job.getType().getCode()).isEqualTo("orange");
        Assertions.assertThat(job.getOwnerUserNickname()).isEqualTo("erik");
    }

    @Test
    public void testDeleteJob() {
        String jobCode = createAnyJob(JobSnapshot.Status.FAILED);
        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();

        Boolean actualWasDeleted = transactionTemplate.execute((transactionStatus) -> {

            // -------------------------
            return service.deleteJob(jobCode);
            // -------------------------

        });

        Assertions.assertThat(actualWasDeleted).isTrue();
        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isFalse();
    }

    @Test
    public void testSetJobProgressPercent() {
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // -------------------------
                service.setJobProgressPercent(jobCode, 66);
                // -------------------------
            }

        });

        Optional<TestJobState> jobStateOptional = tryGetTestJobStateForJob(jobCode);
        Assertions.assertThat(jobStateOptional.isPresent()).isTrue();
        Assertions.assertThat(jobStateOptional.get().status).isEqualTo(JobSnapshot.Status.STARTED);
        Assertions.assertThat(jobStateOptional.get().progressPercentage).isEqualTo(66);
    }

    /**
     * <p>In this test we do find a Job to delete.</p>
     */
    @Test
    public void testClearCompletedExpiredJobs_positive() {
        String jobCode = createAnyJob(JobSnapshot.Status.FINISHED);
        // ^ This one has an expiry 30 mins into the future
        Instant now = Clock.systemUTC().instant().plus(Duration.ofMinutes(60));
        // ^ the job would have expired at this point.

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // -------------------------
                service.clearCompletedExpiredJobs(now);
                // -------------------------
            }

        });

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isFalse();
    }

    /**
     * <p>In this test we do NOT find a Job to delete.</p>
     */
    @Test
    public void testClearCompletedExpiredJobs_negative() {
        String jobCode = createAnyJob(JobSnapshot.Status.FINISHED);
        // ^ This one has an expiry 30 mins into the future.
        Instant now = Clock.systemUTC().instant();
        // ^ has not expired yet.

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();

        transactionTemplate.execute(new TransactionCallbackWithoutResult() {

            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                // -------------------------
                service.clearCompletedExpiredJobs(now);
                // -------------------------
            }

        });

        Assertions.assertThat(tryGetTestJobStateForJob(jobCode).isPresent()).isTrue();
        // ^ not deleted
    }

    @Test
    public void testFindJobs() {
        Instant now = Clock.systemUTC().instant();
        String code1 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        String code2 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        String code3 = createAnyJob("orange", JobSnapshot.Status.FAILED, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));
        String code4 = createAnyJob("orange", JobSnapshot.Status.QUEUED, now.minus(Duration.ofMinutes(4)), now.minus(Duration.ofMinutes(4)));
        String code5 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)));
        String code6 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(6)));
        String code7 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(7)), now.minus(Duration.ofMinutes(7)));
        String code8 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(8)));

        // -------------------------
        List<Job> jobs = transactionTemplate.execute(
                (status) -> service.findJobs(
                        "erik",
                        Set.of(JobSnapshot.Status.STARTED),
                        2,
                        3
                ));
        // -------------------------

        Assertions.assertThat(jobs.stream().map(Job::getCode).toList()).containsExactly(code5, code6, code7);
    }

    @Test
    public void testTotalJobs() {
        Instant now = Clock.systemUTC().instant();
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        createAnyJob("orange", JobSnapshot.Status.FAILED, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));
        createAnyJob("orange", JobSnapshot.Status.QUEUED, now.minus(Duration.ofMinutes(4)), now.minus(Duration.ofMinutes(4)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(6)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(7)), now.minus(Duration.ofMinutes(7)));
        createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(8)));

        // -------------------------
        Long count = transactionTemplate.execute(
                (status) -> service.totalJobs(
                        "erik",
                        Set.of(JobSnapshot.Status.STARTED)
                ));
        // -------------------------

        Assertions.assertThat(count).isEqualTo(6);
    }

    @Test
    public void testTryGetSuppliedJobData() {
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);
        String jobDataCode = createJobSuppliedData(jobCode);

        // -------------------------
        Optional<JobSuppliedData> jobDataOptional = service.tryGetSuppliedJobData(jobDataCode);
        // -------------------------

        Assertions.assertThat(jobDataOptional.isPresent()).isTrue();
    }

    @Test
    public void testTryGetGeneratedJobData() {
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);
        String jobDataCode = createJobGeneratedData(jobCode);

        // -------------------------
        Optional<JobGeneratedData> jobDataOptional = service.tryGetGeneratedJobData(jobDataCode);
        // -------------------------

        Assertions.assertThat(jobDataOptional.isPresent()).isTrue();
    }

    @Test
    public void testCreateGeneratedJobData() {
        String jobDataCode = UUID.randomUUID().toString();
        String jobCode = createAnyJob(JobSnapshot.Status.STARTED);

        transactionTemplate.executeWithoutResult((status) -> {

            // -------------------------
            JobGeneratedData jobData = service.createGeneratedJobData(
                    jobCode,
                    jobDataCode,
                    "bananas",
                    "application/pdf",
                    "none"
            );
            // -------------------------

            Assertions.assertThat(jobData.getCode()).isEqualTo(jobDataCode);
            Assertions.assertThat(jobData.getJobState().getJob().getCode()).isEqualTo(jobCode);
            Assertions.assertThat(jobData.getUseCode()).isEqualTo("bananas");
            Assertions.assertThat(jobData.getEncoding().getCode()).isEqualTo("none");
            Assertions.assertThat(jobData.getMediaType().getCode()).isEqualTo("application/pdf");
        });

        transactionTemplate.executeWithoutResult((status) -> {
            List<String> results = jdbcTemplate.query(
                    SELECT_GENERATED_JOB_DATA,
                    (row, rowNum) -> row.getString(1),
                    jobDataCode);

            Assertions.assertThat(results.size()).isEqualTo(1);
        });
    }

    /**
     * Note that supplied data is created initially without a Job attached.
     */
    @Test
    public void testCreateSuppliedJobData() {
        String jobDataCode = UUID.randomUUID().toString();

        // -------------------------
        JobSuppliedData jobData = service.createSuppliedJobData(
                jobDataCode,
                "bananas",
                "application/pdf",
                "none"
        );
        // -------------------------

        Assertions.assertThat(jobData.getCode()).isEqualTo(jobDataCode);
        Assertions.assertThat(jobData.getJob()).isNull();
        Assertions.assertThat(jobData.getUseCode()).isEqualTo("bananas");
        Assertions.assertThat(jobData.getEncoding().getCode()).isEqualTo("none");
        Assertions.assertThat(jobData.getMediaType().getCode()).isEqualTo("application/pdf");

        List<String> results = jdbcTemplate.query(
                SELECT_SUPPLIED_JOB_DATA,
                (row, rowNum) -> row.getString(1),
                jobDataCode);

        Assertions.assertThat(results.size()).isEqualTo(1);
    }

    private static Stream<Arguments> privateTestUpdateStateStatus() {
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
    @MethodSource("privateTestUpdateStateStatus")
    public void testUpdateStateStatus(JobSnapshot.Status startStatus, JobSnapshot.Status endStatus) {
        String jobCode = createAnyJob(startStatus);
        Instant now = Clock.systemUTC().instant();

        // -------------------------
        service.updateStateStatus(
                jobCode,
                now,
                endStatus
        );
        // -------------------------

        Optional<TestJobState> testJobState = tryGetTestJobStateForJob(jobCode);
        Assertions.assertThat(testJobState.isPresent()).isTrue();
        Assertions.assertThat(testJobState.get().status()).isEqualTo(endStatus);
    }

    @Test
    public void testStreamJobsByTypeAndStatuses() {
        Instant now = Clock.systemUTC().instant();
        String code1 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(1)), now.minus(Duration.ofMinutes(1)));
        String code2 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(2)), now.minus(Duration.ofMinutes(2)));
        String code3 = createAnyJob("orange", JobSnapshot.Status.FAILED, now.minus(Duration.ofMinutes(3)), now.minus(Duration.ofMinutes(3)));
        String code4 = createAnyJob("orange", JobSnapshot.Status.QUEUED, now.minus(Duration.ofMinutes(4)), now.minus(Duration.ofMinutes(4)));
        String code5 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(5)), now.minus(Duration.ofMinutes(5)));
        String code6 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(6)), now.minus(Duration.ofMinutes(6)));
        String code7 = createAnyJob("banana", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(7)), now.minus(Duration.ofMinutes(7)));
        String code8 = createAnyJob("orange", JobSnapshot.Status.STARTED, now.minus(Duration.ofMinutes(8)), now.minus(Duration.ofMinutes(8)));

        // -------------------------
        List<String> jobCodes = transactionTemplate.execute(status -> service.streamJobsByTypeAndStatuses("orange", Set.of(JobSnapshot.Status.STARTED))
                .map(Job::getCode)
                .toList());
        // -------------------------

        // excludes 3, 4 as they are the wrong status and exclude 7 because it has the wrong type code
        Assertions.assertThat(jobCodes).containsExactly(code1, code2, code5, code6, code8);

    }

    @Test
    public void testCreateJob() {
        Instant now = Clock.systemUTC().instant();
        String jobCode = UUID.randomUUID().toString();
        ObjectMapper objectMapper = new ObjectMapper();

        // create some supplied data.
        String suppliedJobDataCode = createJobSuppliedData(null);

        // -------------------------
        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {
                service.createJob(
                        jobCode,
                        "mango",
                        "ralph",
                        now,
                        Duration.ofMinutes(10).toMillis(),
                        objectMapper.createObjectNode(),
                        Set.of(suppliedJobDataCode),
                        false);
            }
        });
        // -------------------------

        Optional<TestJobState> testJobStateOptional = tryGetTestJobStateForJob(jobCode);

        Assertions.assertThat(testJobStateOptional.isPresent()).isTrue();
        Assertions.assertThat(testJobStateOptional.get().status()).isEqualTo(JobSnapshot.Status.QUEUED);

        // check that the supplied data is now associated with the job.

        Assertions.assertThat(getJobDataCodesForJobCode(jobCode)).containsOnly(suppliedJobDataCode);
    }

    /**
     * <p>This will get a job to process, but while it is being processed, the job should not be found
     * by the same method on another transaction.</p>
     */

    @Test
    public void testTryGetNextAvailableJob() {
        String jobCode = createAnyJob(JobSnapshot.Status.QUEUED);


        transactionTemplate.execute(new TransactionCallbackWithoutResult() {
            @Override
            protected void doInTransactionWithoutResult(TransactionStatus status) {

                // -------------------------
                Optional<Job> jobOptional1 = service.tryGetNextAvailableJob();
                // -------------------------

                Assertions.assertThat(jobOptional1.isPresent()).isTrue();
                Assertions.assertThat(jobOptional1.get().getCode()).isEqualTo(jobCode);

                transactionTemplate.execute(new TransactionCallbackWithoutResult() {
                    @Override
                    protected void doInTransactionWithoutResult(TransactionStatus status) {
                        // -------------------------
                        Optional<Job> jobOptional2 = service.tryGetNextAvailableJob();
                        // -------------------------

                        Assertions.assertThat(jobOptional2.isPresent()).isFalse();
                    }
                });
            }
        });
    }

    /**
     * <p>Creates a number of jobs. One of the started ones will be locked by another thread. Get
     * the danging started jobs' codes. This should not include the one that is locked by the other
     * thread because the thread will be working on the job.</p>
     */
    @Test
    public void testGetDanglingStartedJobCodes() {
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
                transactionTemplate.executeWithoutResult(status -> {
                    try {
                        String queriedCode = jdbcTemplate.queryForObject(
                                "SELECT code FROM job.job WHERE code = ? FOR UPDATE SKIP LOCKED",
                                (rs, rowNum) -> rs.getString(1),
                                code2
                        );
                        Assertions.assertThat(queriedCode).isEqualTo(code2);
                    } finally {
                        // so that the test can wait until the row is locked.
                        rowDidLockThreadSem.release();
                    }

                    // keep the row lock thread active until the test is completed
                    Assertions
                            .assertThat(Uninterruptibles.tryAcquireUninterruptibly(testCompleteThreadSem, Duration.ofSeconds(10)))
                            .isTrue();
                });
            });

            Assertions
                    .assertThat(Uninterruptibles.tryAcquireUninterruptibly(rowDidLockThreadSem, Duration.ofSeconds(10)))
                    .isTrue();

            // -------------------------
            Set<String> actualDanglingJobCodes = service.getDanglingStartedJobCodes();
            // -------------------------

            // This does not contain code2 because it is locked and does not contain code 4 because it is failed.
            Assertions.assertThat(actualDanglingJobCodes).containsOnly(code1, code3);

        } finally {
            testCompleteThreadSem.release();
        }

        Uninterruptibles.joinUninterruptibly(thread, Duration.ofSeconds(10));
        Assertions.assertThat(thread.isAlive()).isFalse();
    }

    private Set<String> getJobDataCodesForJobCode(String jobCode) {
        return Set.copyOf(jdbcTemplate.query(
                SELECT_JOB_SUPPLIED_DATA_CODES_FOR_JOB,
                (row, rowNum) -> row.getString(1),
                jobCode));
    }

    private Optional<TestJobState> tryGetTestJobStateForJob(String jobCode) {
        Function<java.sql.Timestamp, Instant> mapTimestampFn = (ts) -> Optional.ofNullable(ts)
                .map(ts1 -> Instant.ofEpochMilli(ts1.getTime()))
                .orElse(null);

        List<TestJobState> results = jdbcTemplate.query(
                SELECT_JOB_STATE_STATUS_CODE_FOR_JOB,
                (row, rowNum) -> new TestJobState(
                        Jobs.mapTimestampsToStatus(
                            mapTimestampFn.apply(row.getTimestamp(2)), // fail_timestamp
                            mapTimestampFn.apply(row.getTimestamp(3)), // cancel_timestamp
                            mapTimestampFn.apply(row.getTimestamp(4)), // finish_timestamp
                            mapTimestampFn.apply(row.getTimestamp(5)), // start_timestamp
                            mapTimestampFn.apply(row.getTimestamp(6)) // queue_timestamp
                        ),
                        row.getInt(1) // progress_percent
                ),
                jobCode);

        switch (results.size()) {
            case 0:
                return Optional.empty();
            case 1:
                return Optional.of(results.getLast());
            default:
                throw new IllegalStateException("more than one job status for [" + jobCode + "]");
        }
    }

    private long getOrCreateJobType(String jobTypeCode) {
        List<Long> ids = jdbcTemplate.query(SELECT_JOB_TYPE, (row, rowNum) -> row.getLong(1), jobTypeCode);

        switch (ids.size()) {
            case 0:
                break;
            case 1:
                return ids.getFirst();
            default:
                throw new IllegalStateException("more than one job type for [" + jobTypeCode + "]");
        }

        long id = nextVal("job.job_type_seq");
        jdbcTemplate.update(INSERT_JOB_TYPE, id, jobTypeCode);
        return id;
    }

    private long getOrCreateJobDataMediaType(String jobDataMediaTypeCode) {
        List<Long> ids = jdbcTemplate.query(
                SELECT_JOB_DATA_MEDIA_TYPE,
                (row, rowNum) -> row.getLong(1), jobDataMediaTypeCode);

        switch (ids.size()) {
            case 0:
                break;
            case 1:
                return ids.getFirst();
            default:
                throw new IllegalStateException("more than one job dta media type for [" + jobDataMediaTypeCode + "]");
        }

        long id = nextVal("job.job_data_media_type_seq");
        jdbcTemplate.update(INSERT_JOB_DATA_MEDIA_TYPE, id, jobDataMediaTypeCode);
        return id;
    }

    private String createJobSuppliedData(String jobCode) {
        String jobDataCode = UUID.randomUUID().toString();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(Clock.systemUTC().millis());
        jdbcTemplate.update(INSERT_JOB_SUPPLIED_DATA,
                nextVal("job.job_supplied_data_seq"),
                jobCode,
                timestamp,
                timestamp,
                jobDataCode,
                "dunno",
                jobDataCode,
                "none",
                getOrCreateJobDataMediaType("application/octet-stream")
        );
        return jobDataCode;
    }

    private String createJobGeneratedData(String jobCode) {
        String jobDataCode = UUID.randomUUID().toString();
        Long jobStateId = jobStateIdForJobCode(jobCode);
        java.sql.Timestamp timestamp = new java.sql.Timestamp(Clock.systemUTC().millis());

        jdbcTemplate.update(INSERT_JOB_GENERATED_DATA,
                nextVal("job.job_generated_data_seq"),
                jobStateId,
                timestamp,
                timestamp,
                jobDataCode,
                "dunno",
                jobDataCode,
                "none",
                getOrCreateJobDataMediaType("application/octet-stream")
        );
        return jobDataCode;
    }

    private Long jobStateIdForJobCode(String jobCode) {
        return jdbcTemplate.queryForObject(SELECT_JOB_STATE_ID_FOR_JOB_CODE, Long.class, jobCode);
    }

    private String createAnyJob(JobSnapshot.Status jobStatus) {
        return createAnyJob("orange", jobStatus, Clock.systemUTC().instant(), Clock.systemUTC().instant());
    }

    private String createAnyJob(String jobTypeCode, JobSnapshot.Status jobStatus, Instant now, Instant queueTimestamp) {
        getOrCreateJobType(jobTypeCode);

        long jobId = nextVal("job.job_seq");
        long jobStateId = nextVal("job.job_state_seq");
        long jobSpecificationId = nextVal("job.job_specification_seq");
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

        jdbcTemplate.update(INSERT_JOB_SPECIFICATION,
                jobSpecificationId, timestamp, timestamp, "{}");
        jdbcTemplate.update(INSERT_JOB_STATE,
                jobStateId, timestamp, timestamp,
                        toTimestampFn.apply(startTimestamp),
                        toTimestampFn.apply(finishTimestamp),
                        toTimestampFn.apply(queueTimestamp),
                        toTimestampFn.apply(failTimestamp),
                        toTimestampFn.apply(cancelTimestamp),
                        50);
        jdbcTemplate.update(INSERT_JOB,
                jobId, timestamp, timestamp, jobCode, jobTypeCode, jobStateId, jobSpecificationId, expiryTimestamp, "erik");

        return jobCode;
    }

    private long nextVal(String sequenceName) {
        return Optional.ofNullable(jdbcTemplate.queryForObject(SELECT_NEXTVAL, Long.class, sequenceName))
                .orElseThrow();
    }

    record TestJobState (JobSnapshot.Status status, Integer progressPercentage) {}

}
