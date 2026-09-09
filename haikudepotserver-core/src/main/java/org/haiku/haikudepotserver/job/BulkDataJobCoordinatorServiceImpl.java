/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.model.PkgDumpExportJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgIconExportArchiveJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class BulkDataJobCoordinatorServiceImpl implements BulkDataJobCoordinatorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BulkDataJobCoordinatorServiceImpl.class);

    private static final int LIMIT_CLEAR_EXPIRED_JOBS = 100;

    private static final Set<JobSnapshot.Status> STATUSES_QUEUED_STARTED_FINISHED = Set.of(
            JobSnapshot.Status.FINISHED, JobSnapshot.Status.STARTED, JobSnapshot.Status.QUEUED);

    private final JobService jobService;

    private final Duration renewAfterRemainingDuration;

    private final Duration renewStanddownSinceLastStartDuration;

    /**
     * <p>When data is requested for a specific {@link NaturalLanguage}, this class will continue to generate updated
     * data for the language for this duration of time.</p>
     */
    private final Duration renewForNaturalLanguageDuration;

    /**
     * <p>When clearing expired Jobs; only remove those that finished after this {@link Duration} ago.</p>
     */
    private final Duration clearExpiredAfterFinishedDuration;

    private final RepositoryService repositoryService;

    private final PkgService pkgService;

    private final NaturalLanguageService naturalLanguageService;

    private final ServerRuntime serverRuntime;

    private final Clock clock;

    /**
     * Lock to make sure that only a single thread of the refresh is happening at a time.
     */
    private final Lock refreshLock = new ReentrantLock();

    public BulkDataJobCoordinatorServiceImpl(
            final Clock clock,
            final ServerRuntime serverRuntime,
            final JobService jobService,
            final RepositoryService repositoryService,
            final PkgService pkgService,
            final NaturalLanguageService naturalLanguageService,
            final Duration renewAfterRemainingDuration,
            final Duration renewStanddownSinceLastStartDuration,
            final Duration renewForNaturalLanguageDuration,
            final Duration clearExpiredAfterFinishedDuration) {

        Preconditions.checkArgument(
                null != renewAfterRemainingDuration && !renewAfterRemainingDuration.isNegative(),
                "bad `renewAfterRemainingDuration` value");
        Preconditions.checkArgument(
                null != renewStanddownSinceLastStartDuration && !renewStanddownSinceLastStartDuration.isNegative(),
                "bad `renewStanddownSinceLastStartDuration` value");
        Preconditions.checkArgument(
                null != renewForNaturalLanguageDuration && !renewForNaturalLanguageDuration.isNegative(),
                "bad `renewForNaturalLanguageDuration` value");
        Preconditions.checkArgument(
                null != clearExpiredAfterFinishedDuration && !clearExpiredAfterFinishedDuration.isNegative(),
                "bad `clearExpiredAfterFinishedDuration` value");

        this.clock = clock;
        this.serverRuntime = serverRuntime;
        this.jobService = jobService;
        this.repositoryService = repositoryService;
        this.pkgService = pkgService;
        this.naturalLanguageService = naturalLanguageService;

        this.renewAfterRemainingDuration = renewAfterRemainingDuration;
        this.renewStanddownSinceLastStartDuration = renewStanddownSinceLastStartDuration;
        this.renewForNaturalLanguageDuration = renewForNaturalLanguageDuration;
        this.clearExpiredAfterFinishedDuration = clearExpiredAfterFinishedDuration;
    }

    @Override
    public String getOrCreatePkgIconExportArchive() {
        PkgIconExportArchiveJobSpecification specification = createPkgIconExportArchiveJobSpecification();
        return getOrCreateBySpecification(specification);
    }

    @Override
    public String getOrCreateRepositoryDumpExport() {
        RepositoryDumpExportJobSpecification specification = createRepositoryDumpExportJobSpecification();
        return getOrCreateBySpecification(specification);
    }

    @Override
    public String getOrCreateReferenceDumpExport(NaturalLanguageCoordinates naturalLanguage) {
        ReferenceDumpExportJobSpecification specification = createReferenceDumpExportJobSpecification(naturalLanguage);
        String jobCode = getOrCreateBySpecification(specification);
        naturalLanguageService.updateUse(naturalLanguage);
        return jobCode;
    }

    @Override
    public String getOrCreatePkgDumpExport(NaturalLanguageCoordinates naturalLanguage, String repositorySourceCode) {
        PkgDumpExportJobSpecification specification = createPkgDumpExportJobSpecification(naturalLanguage, repositorySourceCode);
        String jobCode = getOrCreateBySpecification(specification);
        naturalLanguageService.updateUse(naturalLanguage);
        return jobCode;
    }

    @Override
    public void performRefresh() {

        if (refreshLock.tryLock()) {
            try {
                LOGGER.info("will refresh");

                Instant now = clock.instant();

                Set<NaturalLanguageCoordinates> naturalLanguages = deriveNaturalLanguagesToRenewFor(now);

                if (LOGGER.isInfoEnabled()) {
                    LOGGER.info(
                            "found {} natural languages to refresh for; {}",
                            naturalLanguages.size(),
                            naturalLanguages.stream()
                                    .map(NaturalLanguageCoordinates::getCode)
                                    .sorted()
                                    .collect(Collectors.joining(",")));
                }

                Set<String> repositorySourceCodes = derivedRepositorySourceCodesToRenewFor();
                LOGGER.info("found {} repository source codes to refresh for", repositorySourceCodes.size());

                boolean didRefreshRepositoryDumpExport = maybePerformRepositoryDumpExportRefresh(now);

                boolean didRefreshPkgIconExportArchive = maybePerformPkgIconExportArchiveRefresh(now);

                int countDidRefreshReferenceDumpExport = 0;

                for (NaturalLanguageCoordinates naturalLanguage : naturalLanguages) {
                    if (maybePerformReferenceDumpExportRefresh(now, naturalLanguage)) {
                        countDidRefreshReferenceDumpExport++;
                    }
                }

                int countDidRefreshPkgDumpExport = 0;
                int countMaybeRefreshPkgDumpExport = 0;

                for (NaturalLanguageCoordinates naturalLanguage : naturalLanguages) {
                    for (String repositorySourceCode : repositorySourceCodes) {
                        countMaybeRefreshPkgDumpExport++;
                        if (maybePerformPkgDumpExportRefresh(now, naturalLanguage, repositorySourceCode)) {
                            countDidRefreshPkgDumpExport++;
                        }
                    }
                }

                logAllCounts("did refresh", new AllActionCounts(
                        ActionCounts.singleton(didRefreshRepositoryDumpExport),
                        ActionCounts.singleton(didRefreshPkgIconExportArchive),
                        new ActionCounts(countDidRefreshReferenceDumpExport, naturalLanguages.size()),
                        new ActionCounts(countDidRefreshPkgDumpExport, countMaybeRefreshPkgDumpExport)
                ));
            }
            finally {
                refreshLock.unlock();
            }
        } else {
            LOGGER.warn("was already refreshing --> will skip");
        }
    }

    @Override
    public void clearExpiredJobs() {
        LOGGER.info("will clear expired");

        Instant now = clock.instant();

        Set<NaturalLanguageCoordinates> naturalLanguages = deriveNaturalLanguagesToRenewFor(now);
        LOGGER.info("found {} natural languages to clear expired for", naturalLanguages.size());

        Set<String> repositorySourceCodes = derivedRepositorySourceCodesToRenewFor();
        LOGGER.info("found {} repository source codes to clear expired for", repositorySourceCodes.size());

        ActionCounts repositoryDumpExportCounts = clearExpiredRepositoryDumpExport(now);

        ActionCounts pkgIconExportCounts = clearPkgIconExportArchive(now);

        ActionCounts expiredReferenceDumpCounts = ActionCounts.empty();

        for (NaturalLanguageCoordinates naturalLanguage : naturalLanguages) {
            expiredReferenceDumpCounts = expiredReferenceDumpCounts.add(
                    clearExpiredReferenceDumpExport(now, naturalLanguage));
        }

        ActionCounts expiredPkgDumpExportCounts = ActionCounts.empty();

        for (NaturalLanguageCoordinates naturalLanguage : naturalLanguages) {
            for (String repositorySourceCode : repositorySourceCodes) {
                expiredPkgDumpExportCounts = expiredPkgDumpExportCounts.add(
                        clearExpiredPkgDumpExport(now, naturalLanguage, repositorySourceCode));
            }
        }

        logAllCounts(
                "did clear expired",
                new AllActionCounts(
                        repositoryDumpExportCounts,
                        pkgIconExportCounts,
                        expiredReferenceDumpCounts,
                        expiredPkgDumpExportCounts
                )
        );
    }

    @VisibleForTesting
    ActionCounts clearExpiredRepositoryDumpExport(Instant now) {
        RepositoryDumpExportJobSpecification specification = createRepositoryDumpExportJobSpecification();
        return clearExpiredJobsBySpecification(now, specification);
    }

    /**
     * @return true if it did do a refresh
     */
    @VisibleForTesting
    boolean maybePerformRepositoryDumpExportRefresh(Instant now) {
        RepositoryDumpExportJobSpecification specification = createRepositoryDumpExportJobSpecification();

        Optional<? extends JobSnapshot> jobOptional = jobService.tryGetLatestMatchingJob(
                specification,
                Set.of(JobSnapshot.Status.FINISHED, JobSnapshot.Status.STARTED, JobSnapshot.Status.QUEUED));

        if (jobOptional.isPresent()) {
            JobSnapshot job = jobOptional.get();
            ObjectContext context = serverRuntime.newContext();
            Instant lastRepositoryModifyTimestamp = repositoryService.getLastRepositoryModifyTimestampSecondAccuracy(context).toInstant();

            if (shouldRenew(now, lastRepositoryModifyTimestamp, job)) {
                String renewedJobCode = jobService.submit(specification, Set.of());
                LOGGER.info("repository dump export [{}] renewed as [{}]", job.getGuid(), renewedJobCode);
                return true;
            } else {
                LOGGER.debug("repository dump export [{}] -> renew not necessary", job.getGuid());
            }
        } else {
            String newJobCode = jobService.submit(specification, Set.of());
            LOGGER.info("repository dump export not found -> will generate as [{}]", newJobCode);
            return true;
        }

        return false;
    }

    private ActionCounts clearPkgIconExportArchive(Instant now) {
        PkgIconExportArchiveJobSpecification specification = createPkgIconExportArchiveJobSpecification();
        return clearExpiredJobsBySpecification(now, specification);
    }

    /**
     * @return true if the refresh was performed.
     */
    private boolean maybePerformPkgIconExportArchiveRefresh(Instant now) {
        PkgIconExportArchiveJobSpecification specification = createPkgIconExportArchiveJobSpecification();
        Optional<? extends JobSnapshot> jobOptional = jobService.tryGetLatestMatchingJob(
                specification, STATUSES_QUEUED_STARTED_FINISHED);

        if (jobOptional.isPresent()) {
            JobSnapshot job = jobOptional.get();

            if (shouldRenew(now, null, job)) {
                String renewedJobCode = jobService.submit(specification, Set.of());
                LOGGER.info("pkg icon export archive [{}] renewed as [{}]", job.getGuid(), renewedJobCode);
                return true;
            } else {
                LOGGER.debug("pkg icon export archive [{}] -> renew not necessary", job.getGuid());
            }
        } else {
            String newJobCode = jobService.submit(specification, Set.of());
            LOGGER.info("pkg icon export archive not found -> will generate as [{}]", newJobCode);
            return true;
        }

        return false;
    }

    private ActionCounts clearExpiredReferenceDumpExport(Instant now, NaturalLanguageCoordinates naturalLanguage) {
        ReferenceDumpExportJobSpecification specification = createReferenceDumpExportJobSpecification(naturalLanguage);
        return clearExpiredJobsBySpecification(now, specification);
    }

    /**
     * @return true if the data was refreshed
     */
    private boolean maybePerformReferenceDumpExportRefresh(Instant now, NaturalLanguageCoordinates naturalLanguage) {
        ReferenceDumpExportJobSpecification specification = createReferenceDumpExportJobSpecification(naturalLanguage);
        Optional<? extends JobSnapshot> jobOptional = jobService.tryGetLatestMatchingJob(
                specification, STATUSES_QUEUED_STARTED_FINISHED);

        if (jobOptional.isPresent()) {
            JobSnapshot job = jobOptional.get();

            if (shouldRenew(now, null, job)) {
                String renewedJobCode = jobService.submit(specification, Set.of());
                LOGGER.info("reference dump export [{}] renewed as [{}]", job.getGuid(), renewedJobCode);
                return true;
            } else {
                LOGGER.debug("reference dump export [{}] -> renew not necessary", job.getGuid());
            }
        } else {
            String newJobCode = jobService.submit(specification, Set.of());
            LOGGER.info("reference dump export not found -> will generate as [{}]", newJobCode);
            return true;
        }

        return false;
    }

    private ActionCounts clearExpiredPkgDumpExport(
            Instant now,
            NaturalLanguageCoordinates naturalLanguage,
            String repositorySourceCode) {
        PkgDumpExportJobSpecification specification = createPkgDumpExportJobSpecification(naturalLanguage, repositorySourceCode);
        return clearExpiredJobsBySpecification(now, specification);
    }

    /**
     * @return true if the data was refreshed.
     */
    private boolean maybePerformPkgDumpExportRefresh(
            Instant now,
            NaturalLanguageCoordinates naturalLanguage,
            String repositorySourceCode) {
        PkgDumpExportJobSpecification specification = createPkgDumpExportJobSpecification(naturalLanguage, repositorySourceCode);
        Optional<? extends JobSnapshot> jobOptional = jobService.tryGetLatestMatchingJob(specification, STATUSES_QUEUED_STARTED_FINISHED);

        if (jobOptional.isPresent()) {
            ObjectContext context = serverRuntime.newContext();
            Instant lastPkgModifiedTimestamp = pkgService.getLastModifyTimestampSecondAccuracy(context, repositorySourceCode).toInstant();
            JobSnapshot job = jobOptional.get();

            if (shouldRenew(now, lastPkgModifiedTimestamp, job)) {
                String renewedJobCode = jobService.submit(specification, Set.of());
                LOGGER.info("pkg dump [{}] export renewed as [{}]", job.getGuid(), renewedJobCode);
                return true;
            } else {
                LOGGER.debug("pkg dump export [{}] -> renew not necessary", job.getGuid());
            }
        } else {
            String newJobCode = jobService.submit(specification, Set.of());
            LOGGER.info("pkg dump export not found -> will generate as [{}]", newJobCode);
            return true;
        }

        return false;
    }

    /**
     * @param now                    is what is considered to be the current timestamp.
     * @param persistedDataTimestamp is the actual currently persisted data's timestamp.
     * @param job                    is the {@link Job} under consideration for renewal.
     * @return {@code true} if the job should be renewed.
     */
    private boolean shouldRenew(Instant now, @Nullable Instant persistedDataTimestamp, JobSnapshot job) {
        Preconditions.checkArgument(null != job, "the job must be provided");

        if (renewAfterRemainingDuration.isNegative()) {
            return false;
        }

        switch (job.getStatus()) {
            case QUEUED, STARTED:
                return false;
            case FINISHED:
                break;
            case CANCELLED, FAILED, INDETERMINATE:
                return true;
        }

        if (null == job.getDataTimestamp()) {
            throw new IllegalStateException("the job [%s] must have a data timestamp".formatted(job.getGuid()));
        }

        Optional<Long> ttlOptional = job.tryGetTimeToLiveMillis();

        if (ttlOptional.isEmpty()) {
            LOGGER.error("job [{}] has no ttl", job.getGuid());
            return true;
        }

        Instant expiryTimestamp = Instant.ofEpochMilli(
                job.getQueuedTimestamp().getTime() + ttlOptional.get());

        // If the data is going to expire shortly then replace it well before it does.

        if (expiryTimestamp.minus(renewAfterRemainingDuration).isBefore(now)) {
            LOGGER.info("the job [{}] is going to expire soon so renew it", job.getGuid());
            return true;
        }

        // Make sure that the data is not replaced too quickly after it is generated; otherwise there is no benefit
        // from the publishing process.

        if (job.getStartTimestamp().toInstant().plus(renewStanddownSinceLastStartDuration).isAfter(now)) {
            LOGGER.debug("the job [{}] is too new --> won't consider renew", job.getGuid());
            return false;
        }

        // if the persisted data is greater than the data timestamp of the job.

        if (null != persistedDataTimestamp) {
            if (job.getDataTimestamp().toInstant().isBefore(persistedDataTimestamp)) {
                LOGGER.info(
                        "the job [{}] exists with data [{}] but there is new data [{}] --> will renew",
                        job.getGuid(),
                        job.getDataTimestamp(),
                        persistedDataTimestamp);
                return true;
            }
        }

        return false;
    }

    /**
     * All the popular languages should be expressed in the output, but also any languages for which data has
     * more recently been produced.
     */

    private Set<NaturalLanguageCoordinates> deriveNaturalLanguagesToRenewFor(Instant now) {
        ObjectContext context = serverRuntime.newContext();
        Instant naturalLanguageUseCutoffInstant = now.minus(renewForNaturalLanguageDuration);
        return Sets.union(
                naturalLanguageService.naturalLanguagesUsedSince(naturalLanguageUseCutoffInstant),
                NaturalLanguage.getAllPopular(context)
                        .stream()
                        .map(NaturalLanguage::toCoordinates)
                        .collect(Collectors.toUnmodifiableSet())
        );
    }

    private Set<String> derivedRepositorySourceCodesToRenewFor() {
        ObjectContext context = serverRuntime.newContext();
        return Set.copyOf(ObjectSelect.query(RepositorySource.class)
                .where(RepositorySource.ACTIVE.eq(true))
                .where(RepositorySource.REPOSITORY.dot(Repository.ACTIVE).eq(true))
                .fetchDataRows()
                .column(RepositorySource.CODE)
                .select(context));
    }

    private ActionCounts clearExpiredJobsBySpecification(Instant now, JobSpecification specification) {

        int didClearCount = 0;

        // TODO (andponlin) this needs to be more efficient; can we somehow predicate the list on
        //  more of the information in the specification?

        List<? extends JobSnapshot> jobs = jobService.findJobs(new JobFindRequest(
                        null, // now owner means open to all.
                        specification.getJobTypeCode(),
                        Set.of(JobSnapshot.Status.FINISHED)
                ),
                0,
                LIMIT_CLEAR_EXPIRED_JOBS)
                .stream()
                .filter(j -> j.getJobSpecification().isEquivalent(specification))
                .toList();

        if (jobs.size() >= LIMIT_CLEAR_EXPIRED_JOBS) {
            LOGGER.error(
                    "the jobs for specification of type [{}] are more than the {} limit that can be cleared",
                    specification.getJobTypeCode(),
                    LIMIT_CLEAR_EXPIRED_JOBS);
        }

        if (jobs.size() > 1) {

            LOGGER.info("did find {} jobs matching specification of type [{}]", jobs.size(), specification.getJobTypeCode());

            List<? extends JobSnapshot> sortedJobs = jobs
                    .stream()
                    .sorted(Comparator.comparing(JobSnapshot::getFinishTimestamp))
                    .toList();

            List<? extends JobSnapshot> jobsToRemove = sortedJobs.subList(0, sortedJobs.size() - 1);

            // Because somebody may be still streaming data from one of the older jobs, we need to only deal with those
            // significantly in the past.

            Instant cutOffTimestamp = now.minus(clearExpiredAfterFinishedDuration);

            List<? extends JobSnapshot> oldJobsToRemove = jobsToRemove
                    .stream()
                    .filter(j -> j.getFinishTimestamp().toInstant().isBefore(cutOffTimestamp))
                    .toList();

            LOGGER.info(
                    "removing {} jobs for specification of type [{}]",
                    oldJobsToRemove.size(),
                    specification.getJobTypeCode());

            didClearCount = oldJobsToRemove.size();
            oldJobsToRemove.forEach(js -> jobService.removeJob(js.getGuid()));
        } else {
            LOGGER.debug("there are no expired jobs to clear of type [{}]", specification.getJobTypeCode());
        }

        return new ActionCounts(didClearCount, jobs.size());
    }

    private String getOrCreateBySpecification(JobSpecification specification) {
        Optional<? extends JobSnapshot> jobOptional = jobService.tryGetLatestMatchingJob(specification, STATUSES_QUEUED_STARTED_FINISHED);

        if (jobOptional.isPresent()) {
            return jobOptional.get().getGuid();
        }

        return jobService.submit(specification, STATUSES_QUEUED_STARTED_FINISHED);
    }

    private PkgIconExportArchiveJobSpecification createPkgIconExportArchiveJobSpecification() {
        PkgIconExportArchiveJobSpecification specification = new PkgIconExportArchiveJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        return specification;
    }

    private PkgDumpExportJobSpecification createPkgDumpExportJobSpecification(
            NaturalLanguageCoordinates naturalLanguage,
            String repositorySourceCode
    ) {
        PkgDumpExportJobSpecification specification = new PkgDumpExportJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        specification.setRepositorySourceCode(repositorySourceCode);
        specification.setNaturalLanguageCode(naturalLanguage.getCode());
        return specification;
    }

    private RepositoryDumpExportJobSpecification createRepositoryDumpExportJobSpecification() {
        RepositoryDumpExportJobSpecification specification = new RepositoryDumpExportJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        return specification;
    }

    private ReferenceDumpExportJobSpecification createReferenceDumpExportJobSpecification(NaturalLanguageCoordinates naturalLanguage) {
        ReferenceDumpExportJobSpecification specification = new ReferenceDumpExportJobSpecification();
        specification.setGuid(UUID.randomUUID().toString());
        specification.setNaturalLanguageCode(naturalLanguage.getCode());
        specification.setFilterForSimpleTwoCharLanguageCodes(false);
        return specification;
    }

    private void logAllCounts(String action, AllActionCounts allActionCounts) {
        LOGGER.info(
                "{}; {} repository dump exports, {} icon export archives, {} reference dump exports, {} pkg dump exports",
                action,
                allActionCounts.repositoryDumpExports(),
                allActionCounts.pkgIconExportArchives(),
                allActionCounts.referenceDumpExports(),
                allActionCounts.pkgDumpExports());
    }

    /**
     * <p>Records the count of actions taken against the categories of data. This is used for logging.</p>
     */
    record AllActionCounts(
            ActionCounts repositoryDumpExports,
            ActionCounts pkgIconExportArchives,
            ActionCounts referenceDumpExports,
            ActionCounts pkgDumpExports
    ) {
    }

    record ActionCounts(
            int refreshed,
            int total
    ) {

        static ActionCounts singleton(boolean value) {
            return new ActionCounts(value ? 1 : 0, 1);
        }

        static ActionCounts empty() {
            return new ActionCounts(0, 0);
        }

        ActionCounts add(ActionCounts other) {
            return new ActionCounts(refreshed + other.refreshed(), total + other.total());
        }

        @NotNull
        @Override
        public String toString() {
            return "%d/%d".formatted(refreshed, total);
        }

    }

}
