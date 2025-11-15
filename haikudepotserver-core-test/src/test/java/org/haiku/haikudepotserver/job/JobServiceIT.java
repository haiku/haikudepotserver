/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import com.google.common.io.CharStreams;
import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.stream.IntStream;
import java.util.stream.Stream;

@ContextConfiguration(classes = TestConfig.class)
public class JobServiceIT extends AbstractIntegrationTest {

    private final static int TOLERANCE_SECONDS = 120;

    @Resource
    private JobService jobService;

    @Resource
    private TestLockableJobRunner testLockableJobRunner;

    @BeforeEach
    public void setUp() {
        if (!jobService.awaitAllJobsFinishedUninterruptibly(TimeUnit.SECONDS.toMillis(10))) {
            throw new IllegalStateException("the job service is still busy, but needs to be clean for the tests to proceed.");
        }

        LOGGER.info("did clear out job service");
    }

    /**
     * <p>This will be a bit of an unstable test (non-repeatable) because it is
     * going to drive jobs into the job service and see them all run correctly.
     * It introduces some random delays.</p>
     */

    @Test
    public void testHappyDays() {

        // -------------------------
        List<String> guids = IntStream.of(1, 2, 3, 4)
                .mapToObj((i) -> new TestNumberedLinesJobSpecification(3, 500L))
                .map((spec) -> jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_NONE))
                .toList();
        String immediateGuid = jobService.immediate(new TestNumberedLinesJobSpecification(3, 500L), false);
        // -------------------------

        Stream.concat(guids.stream(), Stream.of(immediateGuid))
                .forEach((guid) -> {

                    jobService.awaitJobFinishedUninterruptibly(guid, TimeUnit.SECONDS.toMillis(15));

                    try {
                        Optional<? extends JobSnapshot> jobSnapshotOptional = jobService.tryGetJob(guid);
                        Assertions.assertThat(jobSnapshotOptional.isPresent()).isTrue();
                        Assertions.assertThat(jobSnapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

                        Set<String> dataGuids = jobSnapshotOptional.get().getGeneratedDataGuids();
                        Assertions.assertThat(dataGuids.size()).isEqualTo(1);
                        String dataGuid = dataGuids.iterator().next();

                        Optional<JobDataWithByteSource> jobDataOptional = jobService.tryObtainData(dataGuid);
                        Assertions.assertThat(jobDataOptional.isPresent()).isTrue();

                        try (
                                InputStream inputStream = jobDataOptional.get().getByteSource().openStream();
                                Reader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
                        ) {
                            Assertions.assertThat(CharStreams.toString(reader)).isEqualTo("0\n1\n2\n");
                        }
                    } catch (IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    }
                });

    }

    /**
     * This test will allow two jobs to go through.  Then it will load another and lock it and put another
     * into the queue.  We can then check the statuses of the jobs and also check that if another is
     * submitted with coalescing, that the correct code is returned.
     */

    @Test
    public void testCoalescePicksTheMostRecentFinished() {

        // make sure that no old jobs from previous tests are coming through.

        Assertions.assertThat(
                jobService.awaitAllJobsFinishedUninterruptibly(TimeUnit.MILLISECONDS.convert(3, TimeUnit.SECONDS))
        ).isTrue();

        // -------------------------
        // setup the initial situation

        String job1FinishedGuid = jobService.submit(new TestLockableJobSpecification("1"), JobSnapshot.COALESCE_STATUSES_NONE);
        jobService.awaitJobFinishedUninterruptibly(job1FinishedGuid, TimeUnit.SECONDS.toMillis(TOLERANCE_SECONDS));
        String job2FinishedGuid = jobService.submit(new TestLockableJobSpecification("2"), JobSnapshot.COALESCE_STATUSES_NONE);
        jobService.awaitJobFinishedUninterruptibly(job2FinishedGuid, TimeUnit.SECONDS.toMillis(TOLERANCE_SECONDS));

        TestLockableJobSpecification jobSpecification3 = new TestLockableJobSpecification("3");
        Lock job3Lock = testLockableJobRunner.getLock(jobSpecification3.getLockId());

        String job3StartedGuid;
        String job4QueuedGuid;

        job3Lock.lock();

        try {
            job3StartedGuid = jobService.submit(jobSpecification3, JobSnapshot.COALESCE_STATUSES_NONE);
            job4QueuedGuid = jobService.submit(new TestLockableJobSpecification("4"), JobSnapshot.COALESCE_STATUSES_NONE);
            // -------------------------

            assertStatus(job1FinishedGuid, JobSnapshot.Status.FINISHED);
            assertStatus(job2FinishedGuid, JobSnapshot.Status.FINISHED);
            assertStatus(job3StartedGuid, JobSnapshot.Status.STARTED);
            assertStatus(job4QueuedGuid, JobSnapshot.Status.QUEUED);

            // check some coalescing works.
            Assertions.assertThat(jobService.submit(new TestLockableJobSpecification("4-b"), JobSnapshot.COALESCE_STATUSES_QUEUED))
                    .isEqualTo(job4QueuedGuid);

            Assertions.assertThat(jobService.submit(new TestLockableJobSpecification("3-b"), JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED))
                    .isEqualTo(job3StartedGuid);

            // expected is the latest finished one
            Assertions.assertThat(jobService.submit(new TestLockableJobSpecification("1-b"), JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED_FINISHED))
                    .isEqualTo(job2FinishedGuid);

            // -------------------------
            // release the last two jobs and watch they come through OK.

        } finally {
            job3Lock.unlock();
        }

        jobService.awaitJobFinishedUninterruptibly(job3StartedGuid, TimeUnit.SECONDS.toMillis(TOLERANCE_SECONDS));
        jobService.awaitJobFinishedUninterruptibly(job4QueuedGuid, TimeUnit.SECONDS.toMillis(TOLERANCE_SECONDS));
        // -------------------------

        assertStatus(job3StartedGuid, JobSnapshot.Status.FINISHED);
        assertStatus(job3StartedGuid, JobSnapshot.Status.FINISHED);

    }

    /**
     * <p>Tests what happens if a job is submitted but there's no runner for it.</p>
     */
    @Test
    public void testJobWithoutRunner() {
        Assertions.assertThat(
                jobService.awaitAllJobsFinishedUninterruptibly(TimeUnit.MILLISECONDS.convert(3, TimeUnit.SECONDS))
        ).isTrue();

        // -------------------------
        String jobGuid = jobService.submit(new WithoutRunnerJobSpecification(), JobSnapshot.COALESCE_STATUSES_NONE);
        // -------------------------

        jobService.awaitJobFinishedUninterruptibly(jobGuid, TimeUnit.SECONDS.toMillis(TOLERANCE_SECONDS));

        assertStatus(jobGuid, JobSnapshot.Status.FAILED);
    }

    private boolean checkStatus(String guid, JobSnapshot.Status status) {
        return jobService.tryGetJob(guid)
                .filter(js -> js.getStatus() == status)
                .isPresent();
    }

    private void assertStatus(String guid, JobSnapshot.Status status) {
        Awaitility.with()
                .atMost(TOLERANCE_SECONDS, TimeUnit.SECONDS)
                .pollDelay(2, TimeUnit.SECONDS)
                .until(() -> checkStatus(guid, status));
    }

    /**
     * <p>This job specification type has no runner; if we submit this one then it should
     * fail.</p>
     */
    private final static class WithoutRunnerJobSpecification extends AbstractJobSpecification {
    }


}
