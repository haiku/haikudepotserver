/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.assertj.core.api.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Job;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguageUse;
import org.haiku.haikudepotserver.job.model.JobFindRequest;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgDumpExportJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@ContextConfiguration(classes = TestConfig.class)
public class BulkDataJobCoordinatorServiceImplIT extends AbstractIntegrationTest {

    @Resource
    private BulkDataJobCoordinatorServiceImpl coordinatorService;

    @Resource
    private JobService jobService;

    @BeforeEach
    public void setup() {
        integrationTestSupportService.createStandardTestData();
    }

    // ------------------------------
    // TESTS WITH REPOSITORY BULK EXPORT -- RENEWAL

    /**
     * <p>Checks to see if there is an existing Job present for the dump; if not then it will
     * enqueue such a job.</p>
     */
    @Test
    public void testMaybePerformRepositoryDumpExportRefresh() {
        // GIVEN
        // No jobs are present.

        // WHEN
        coordinatorService.maybePerformRepositoryDumpExportRefresh(Instant.now());

        awaitAllJobsFinishedUninterruptibly();

        // THEN
        {
            ObjectContext context = serverRuntime.newContext();
            List<? extends JobSnapshot> jobs = jobService.findJobs(
                    new JobFindRequest(null, "repositorydumpexport", null),
                    0,
                    10);
            Assertions.assertThat(jobs).hasSize(1);
        }
    }

    /**
     * <p>There is an existing job for this dump so we don't need to create a new one.</p>
     */
    @Test
    public void testMaybePerformRepositoryDumpExportRefresh_existing() {
        // GIVEN
        RepositoryDumpExportJobSpecification specification = new RepositoryDumpExportJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        String jobCode = jobService.immediate(specification, true);

        // WHEN
        coordinatorService.maybePerformRepositoryDumpExportRefresh(Instant.now());

        awaitAllJobsFinishedUninterruptibly();

        // THEN
        // we expect to see only the existing job present and no new job added.

        {
            List<? extends JobSnapshot> jobs = jobService.findJobs(
                    new JobFindRequest(null, "repositorydumpexport", null),
                    0,
                    10);
            Assertions.assertThat(jobs).hasSize(1);
            Assertions.assertThat(jobs.getLast().getGuid()).isEqualTo(jobCode);
        }
    }

    /**
     * <p>There is an existing job for this dump, but it has expired and a new one needs to be dropped in; a
     * renewal.</p>
     */
    @Test
    public void testMaybePerformRepositoryDumpExportRefresh_existingButTooOld() {
        // GIVEN
        RepositoryDumpExportJobSpecification specification = new RepositoryDumpExportJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        String jobCode = jobService.immediate(specification, true);

        // Adjust the existing job so that it is nearly expired; causing the renewal to
        // be triggered. The renewal should occur ~1h before the expiry.

        {
            Instant now = Instant.now();
            Instant queueTimestampInstant = now
                    .minusMillis(specification.tryGetTimeToLiveMillis().orElseThrow())
                    .plus(Duration.ofMinutes(30));

            ObjectContext context = serverRuntime.newContext();
            Job job = Job.getByCode(context, jobCode);
            job.setQueueTimestamp(new java.sql.Timestamp(queueTimestampInstant.toEpochMilli()));
            context.commitChanges();
        }

        // WHEN
        coordinatorService.maybePerformRepositoryDumpExportRefresh(Instant.now());

        awaitAllJobsFinishedUninterruptibly();

        // THEN
        // we expect to see the one that was added earlier, but also the new one from the renewal.

        {
            List<? extends JobSnapshot> jobs = jobService.findJobs(
                    new JobFindRequest(null, "repositorydumpexport", null),
                    0,
                    10);
            Assertions.assertThat(jobs).hasSize(2);
            Assertions.assertThat(jobs.getLast().getGuid()).isEqualTo(jobCode);
            // the next one is the one that was added.
        }
    }

    /**
     * <p>There is an existing job for this dump. There's newer data so the job should renew and, it does
     * because the old data was generated older than the stand-down period.</p>
     */
    @Test
    public void testMaybePerformRepositoryDumpExportRefresh_newerData() {
        // GIVEN
        Instant now = Instant.now();

        RepositoryDumpExportJobSpecification specification = new RepositoryDumpExportJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        String jobCode = jobService.immediate(specification, true);

        // Adjust the existing job so that it is a wee bit older but not expired; if it's too new
        // then it won't re-generate.

        {
            Instant startTimestampInstant = now.minus(Duration.ofMinutes(20)); // > 10min
            ObjectContext context = serverRuntime.newContext();
            Job job = Job.getByCode(context, jobCode);
            job.setStartTimestamp(new java.sql.Timestamp(startTimestampInstant.toEpochMilli()));
            job.setDataTimestamp(new java.sql.Timestamp(startTimestampInstant.toEpochMilli()));
            context.commitChanges();
        }

        // WHEN
        coordinatorService.maybePerformRepositoryDumpExportRefresh(now);

        awaitAllJobsFinishedUninterruptibly();

        // THEN
        // we expect to see the one that was added earlier, but also the new one from the renewal.

        {
            List<? extends JobSnapshot> jobs = jobService.findJobs(
                    new JobFindRequest(null, "repositorydumpexport", null),
                    0,
                    10);
            Assertions.assertThat(jobs).hasSize(2);
            Assertions.assertThat(jobs.getLast().getGuid()).isEqualTo(jobCode);
            // the next one is the one that was added.
        }
    }

    /**
     * <p>There is an existing job for this dump. There is newer data but the old data was generated within
     * the stand-down so no renewal occurs.</p>
     */
    @Test
    public void testMaybePerformRepositoryDumpExportRefresh_newerDataButWithinStanddown() {
        // GIVEN
        RepositoryDumpExportJobSpecification specification = new RepositoryDumpExportJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        String jobCode = jobService.immediate(specification, true);

        // Adjust the existing job so that it is a wee bit older bit not expired; if it's too new
        // then it won't re-generate.

        {
            Instant now = Instant.now();
            Instant startTimestampInstant = now.minus(Duration.ofMinutes(5)); // < 10min
            ObjectContext context = serverRuntime.newContext();
            Job job = Job.getByCode(context, jobCode);
            job.setStartTimestamp(new java.sql.Timestamp(startTimestampInstant.toEpochMilli()));
            context.commitChanges();
        }

        // WHEN
        coordinatorService.maybePerformRepositoryDumpExportRefresh(Instant.now());

        awaitAllJobsFinishedUninterruptibly();

        // THEN
        // we expect to see the one that was added earlier, but also the new one from the renewal.

        {
            List<? extends JobSnapshot> jobs = jobService.findJobs(
                    new JobFindRequest(null, "repositorydumpexport", null),
                    0,
                    10);
            Assertions.assertThat(jobs).hasSize(1);
            Assertions.assertThat(jobs.getLast().getGuid()).isEqualTo(jobCode);
        }
    }

    // ------------------------------
    // TESTS WITH REPOSITORY BULK EXPORT -- CLEAR

    /**
     * <p>Tests that the clear function will remove any old Jobs of a specific type but only after a
     * certain age.</p>
     */

    @Test
    public void testClearExpiredRepositoryDumpExport() {
        // GIVEN
        List<String> jobCodes = IntStream.range(0, 5)
                .mapToObj(_ -> {
                    RepositoryDumpExportJobSpecification specification = new RepositoryDumpExportJobSpecification();
                    specification.setGuid(UUID.randomUUID().toString());
                    return jobService.immediate(specification, false);
                })
                .toList();

        {
            Instant now = Instant.now();
            Instant finishTimestampInstant = now.minus(Duration.ofMinutes(70)); // more than 1 ago

            ObjectContext context = serverRuntime.newContext();
            jobCodes.subList(0, 2).forEach(jobCode -> {
                Job job = Job.getByCode(context, jobCode);
                job.setFinishTimestamp(new java.sql.Timestamp(finishTimestampInstant.toEpochMilli()));
            });

            context.commitChanges();
        }

        // WHEN
        coordinatorService.clearExpiredRepositoryDumpExport(Instant.now());

        // THEN
        List<? extends JobSnapshot> jobs = jobService.findJobs(
                new JobFindRequest(null, "repositorydumpexport", null),
                0,
                10);

        Assertions.assertThat(jobs).hasSize(3);
        Assertions.assertThat(jobs.stream().map(JobSnapshot::getGuid).toList())
                .containsExactly(jobCodes.get(4), jobCodes.get(3), jobCodes.get(2));
    }

    // ------------------------------
    // GENERAL RENEWAL TEST


    /**
     * <p>This test will trigger a general refresh of the bulk data generation. It will make one of the languages
     * recently used to check that this language is generated for. All of the test repos should be generated for.
     * </p>
     */

    @Test
    public void testPerformRefresh() {
        // GIVEN
        {
            ObjectContext context = serverRuntime.newContext();
            NaturalLanguage maoriLanguage = NaturalLanguage.getByCode(context, "mi");
            NaturalLanguageUse maoriUse = context.newObject(NaturalLanguageUse.class);
            maoriUse.setNaturalLanguage(maoriLanguage);
            maoriUse.setCount(6L);
            maoriUse.setLastUseTimestamp(new java.sql.Timestamp(Instant.now().toEpochMilli()));
            context.commitChanges();
        }

        // WHEN
        coordinatorService.performRefresh();

        awaitAllJobsFinishedUninterruptibly();

        // THEN

        {
            List<? extends JobSnapshot> jobs = jobService.findJobs(
                    new JobFindRequest(null, "pkgdumpexport", null),
                    0,
                    100);

            Set<NaturalLanguageAndRepositorySourceCode> actualCodes = jobs.stream()
                    .map(js -> (PkgDumpExportJobSpecification) js.getJobSpecification())
                    .map(spec -> new NaturalLanguageAndRepositorySourceCode(
                            spec.getNaturalLanguageCode(),
                            spec.getRepositorySourceCode()))
                    .collect(Collectors.toUnmodifiableSet());

            Set<String> expectedNaturalLanguageCodes = Set.of("de", "es", "zh", "fr", "en", "ja", "pt", "ru", "mi");
            Set<String> expectedRepositorySourceCodes = Set.of("testreposrc_xyz_x86_gcc2", "testreposrc_xyz");
            Set<NaturalLanguageAndRepositorySourceCode> expectedCodes = expectedRepositorySourceCodes.stream()
                    .flatMap(rsc -> expectedNaturalLanguageCodes.stream()
                            .map(nlc -> new NaturalLanguageAndRepositorySourceCode(nlc, rsc)))
                    .collect(Collectors.toUnmodifiableSet());

            Assertions.assertThat(actualCodes).isEqualTo(expectedCodes);
        }
    }

    private void awaitAllJobsFinishedUninterruptibly() {
        jobService.awaitAllJobsFinishedUninterruptibly(10_000L);
    }

    record NaturalLanguageAndRepositorySourceCode(String naturalLanguageCode, String repositorySourceCode) {
    }

}