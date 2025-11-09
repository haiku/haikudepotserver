/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.jpa.JpaJobService;
import org.haiku.haikudepotserver.job.jpa.model.JobGeneratedData;
import org.haiku.haikudepotserver.job.jpa.model.JobState;
import org.haiku.haikudepotserver.job.jpa.model.JobSuppliedData;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>An instance of the {@link org.haiku.haikudepotserver.job.model.JobService} which
 * is distributed. It will coordinate with other instances through a database which
 * is accessed via JPA.</p>
 */

public class DbDistributedJobServiceImpl extends AbstractExecutionThreadService implements JobService {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbDistributedJobServiceImpl.class);

    private final static long TTL_DEFAULT = TimeUnit.HOURS.toMillis(2);

    /**
     * No job should still be around after this length of time. Maybe if jobs were stopped part way through by a
     * system failure then they may hang around in the database. This TTL will ensure that they too get purged.
     */
    private final static long TTL_MAX = TimeUnit.DAYS.toMillis(5);

    private final static long DELAY_CHECK_AWAIT_FINISHED_SECONDS = 2;

    private final TransactionTemplate transactionTemplate;

    private final ObjectMapper objectMapper;

    private final DataStorageService dataStorageService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final JpaJobService jpaJobService;

    private final Clock clock;

    private Collection<JobRunner<?>> jobRunners = null;

    private final ReentrantLock workLock = new ReentrantLock();
    private final Condition hasWork = workLock.newCondition();
    private final RetryTemplate contentedDataRetryTemplate = createContentedDataRetryTemplate();

    public DbDistributedJobServiceImpl(
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            DataStorageService dataStorageService,
            Collection<JobRunner<?>> jobRunners,
            ApplicationEventPublisher applicationEventPublisher,
            JpaJobService jpaJobService
    ) {
        this.clock = Clock.systemUTC();
        this.objectMapper = objectMapper;
        this.dataStorageService = dataStorageService;
        this.jobRunners = jobRunners;
        this.applicationEventPublisher = applicationEventPublisher;
        this.jpaJobService = jpaJobService;

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @PostConstruct
    public void init() {
        startAsync();
        awaitRunning();
        signalHasWork();
    }

    @PreDestroy
    public void tearDown() {
        stopAsync();
        signalHasWork();
        awaitTerminated();
    }

    /**
     * <p>Runs continuously checking for new work. Should a failure occur, it will
     * retry with a backoff.</p>
     */
    @Override
    protected void run() throws Exception {
        RetryTemplate retryTemplate = createRunRetryTemplate();

        try {
            retryTemplate.execute((RetryCallback<Object, Throwable>) context -> {
                loopAwaitingAndRunningAvailableJobs();
                return Boolean.TRUE;
            });
        } catch (Throwable e) {
            LOGGER.error("failure to retry listening for pg events", e);
        }
    }

    @EventListener
    public void onApplicationEvent(JobAvailableEvent event) {
        signalHasWork();
    }

    /**
     * <p>Trigger a check for work. It will try to get the lock (if not then it's already
     * processing jobs) and will then trigger processing of jobs.</p>
     */

    private void signalHasWork() {
        if (workLock.tryLock()) {
            try {
                hasWork.signal();
            } finally {
                workLock.unlock();
            }
        }
    }

    @Override
    public boolean awaitAllJobsFinishedUninterruptibly(long timeout) {
        Preconditions.checkArgument(timeout >= 0, "the timeout is negative");

        Instant now = clock.instant();

        while (timeout < 0 || Duration.between(now, clock.instant()).toMillis() < timeout) {
            if (0 == jpaJobService.countNotFinished()) {
                return true;
            }

            Uninterruptibles.sleepUninterruptibly(
                    DELAY_CHECK_AWAIT_FINISHED_SECONDS,
                    TimeUnit.SECONDS);
        }

        return false;
    }

    @Override
    public boolean awaitJobFinishedUninterruptibly(String guid, long timeout) {
        Preconditions.checkArgument(timeout >= 0, "the timeout is negative");
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");

        Instant now = clock.instant();

        while (timeout < 0 || Duration.between(now, clock.instant()).toMillis() < timeout) {
            if (!jpaJobService.isFinished(guid)) {
                Uninterruptibles.sleepUninterruptibly(
                        DELAY_CHECK_AWAIT_FINISHED_SECONDS,
                        TimeUnit.SECONDS);
            } else {
                return true;
            }
        }

        return false;
    }

    /**
     * <p>Store the job and then trigger processing it.</p>
     */
    @Override
    public String submit(JobSpecification specification, Set<JobSnapshot.Status> coalesceForStatuses) {
        Preconditions.checkArgument(null != specification, "the specification must be provided");

        if (CollectionUtils.isNotEmpty(coalesceForStatuses)) {
            Optional<Job> existingJobOptional = getMatchingJob(specification, coalesceForStatuses);

            if (existingJobOptional.isPresent()) {
                return existingJobOptional.get().getGuid();
            }
        }

        persistNewJobTransactionally(specification, false); // not started
        applicationEventPublisher.publishEvent(new JobAvailableEvent());
        return specification.getGuid();
    }

    @Override
    public String immediate(JobSpecification specification, boolean coalesceFinished) {
        Preconditions.checkArgument(null != specification, "the specification must be provided");

        if (coalesceFinished) {
            Optional<Job> existingJobOptional = getMatchingJob(specification, Set.of(JobSnapshot.Status.FINISHED));

            if (existingJobOptional.isPresent()) {
                return existingJobOptional.get().getGuid();
            }
        }

        persistNewJobTransactionally(specification, true); // started
        runSpecificationInCurrentThread(specification);

        return specification.getGuid();
    }

    @Override
    public void setJobFailTimestamp(String guid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");
        updateStateStatus(guid, JobSnapshot.Status.FAILED);
    }

    @Override
    public void setJobCancelTimestamp(String guid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");
        updateStateStatus(guid, JobSnapshot.Status.CANCELLED);
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJob(String guid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");
        return transactionTemplate.execute(
                (transactionStatus) -> jpaJobService.tryGetJob(guid).map(this::mapJpaJobToJob)
        );
    }

    @Transactional
    @Override
    public void removeJob(String guid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");
        transactionTemplate.execute(
                (transactionStatus) -> jpaJobService.deleteJob(guid)
        );
    }

    @Transactional
    @Override
    public void setJobProgressPercent(String guid, Integer progressPercent) {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");

        if (progressPercent == null || progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progress percent must be between 0 and 100; [" + progressPercent + "]");
        }

        transactionTemplate.executeWithoutResult((transactionStatus) -> {
            if (jpaJobService.setJobProgressPercent(guid, progressPercent)) {
                LOGGER.info("job [{}] progress to {}%%", guid, progressPercent);
            }
        });
    }

    @Override
    public void clearExpiredJobs() {
        transactionTemplate.executeWithoutResult((transactionStatus) -> {
            // some started jobs may have been forcefully stopped (eg; JVM stopped) and the started job would be left
            // dangling. This will ensure that those jobs are marked as failed.

            LOGGER.info("will correct dangling started jobs");
            correctDanglingStartedJobs();
            LOGGER.info("did correct dangling started jobs");

            // clear any jobs which are completed and have naturally expired.
            LOGGER.info("will clear completed expired jobs");
            long clearedCompletedExpiredJobs = jpaJobService.clearCompletedExpiredJobs(clock.instant());
            LOGGER.info("did clear {} completed expired jobs", clearedCompletedExpiredJobs);

            // Some jobs may be still in the database but have failed owing to system reasons. This will clean those
            // up as well.
            LOGGER.info("will clear expired jobs");
            long clearedExpiredJobs = jpaJobService.clearExpiredJobs(clock.instant().minus(TTL_MAX, ChronoUnit.MILLIS));
            LOGGER.info("did clear {} expired jobs", clearedCompletedExpiredJobs);
        });
    }

    @Override
    public List<? extends JobSnapshot> findJobs(
            @Nullable User user,
            @Nullable Set<JobSnapshot.Status> statuses,
            int offset,
            int limit) {
        Preconditions.checkArgument(offset >= 0, "bad offset");
        Preconditions.checkArgument(limit > 0, "bad limit");
        return transactionTemplate.execute(
                (transactionStatus) -> jpaJobService.findJobs(
                                Optional.ofNullable(user).map(User::getNickname).orElse(null),
                                statuses,
                                offset,
                                limit)
                        .stream()
                        .map(this::mapJpaJobToJob)
                        .toList());
    }

    @Override
    public int totalJobs(
            @Nullable User user,
            @Nullable Set<JobSnapshot.Status> statuses) {
        Long value = transactionTemplate.execute(
                (transactionStatus) -> jpaJobService.totalJobs(
                        Optional.ofNullable(user).map(User::getNickname).orElse(null),
                        statuses));

        if (null == value) {
            return 0;
        }

        return value.intValue();
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(jobDataGuid), "the data guid is required");
        return transactionTemplate.execute(
                (transactionStatus) -> {
                    // TODO (andponlin) change the interface semantics so that it either gets generated or supplied
                    //  data.
                    Optional<JobSuppliedData> jpaSuppliedDataOptional = jpaJobService.tryGetSuppliedJobData(jobDataGuid);

                    if (jpaSuppliedDataOptional.isPresent()) {
                        return Optional.of(mapJpaJobToJob(jpaSuppliedDataOptional.get().getJob()));
                    }

                    Optional<JobGeneratedData> jpaGeneratedDataOptional = jpaJobService.tryGetGeneratedJobData(jobDataGuid);

                    if (jpaGeneratedDataOptional.isPresent()) {
                        return Optional.of(mapJpaJobToJob(jpaGeneratedDataOptional.get().getJobState().getJob()));
                    }

                    return Optional.empty();
                }
        );
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForSuppliedData(String suppliedDataGuid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(suppliedDataGuid), "the supplied data guid is required");
        return transactionTemplate.execute(
                (transactionStatus) -> jpaJobService.tryGetSuppliedJobData(suppliedDataGuid)
                        .map(jd -> mapJpaJobToJob(jd.getJob()))
        );
    }

    @Override
    public String deriveDataFilename(String jobDataGuid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobDataGuid));
        Optional<? extends JobSnapshot> jobOptional = tryGetJobForData(jobDataGuid);

        Instant timestampInstant = jobOptional
                .map(snapshot -> switch (snapshot.getStatus()) {
                    case FINISHED -> snapshot.getFinishTimestamp().toInstant();
                    default -> snapshot.getQueuedTimestamp().toInstant();
                })
                .orElseGet(clock::instant);

        return String.format(
                "hds_%s_%s_%s.%s",
                jobOptional.map(JobSnapshot::getJobTypeCode).orElse("jobdata"),
                DateTimeHelper.create14DigitDateTimeFormat().format(timestampInstant),
                jobDataGuid.substring(0, 4),
                tryGetData(jobDataGuid).map(Jobs::deriveExtension).orElse(Jobs.EXTENSION_DAT));
    }

    @Override
    public Optional<JobData> tryGetData(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid), "the guid is required");
        return transactionTemplate.execute(
                (transactionStatus) -> {
                    // TODO (andponlin) change the interface semantics so that it either gets generated or supplied
                    //  data.
                    Optional<JobSuppliedData> jpaSuppliedDataOptional = jpaJobService.tryGetSuppliedJobData(guid);

                    if (jpaSuppliedDataOptional.isPresent()) {
                        return Optional.of(mapJpaJobSuppliedDataToJobData(jpaSuppliedDataOptional.get()));
                    }

                    Optional<JobGeneratedData> jpaGeneratedDataOptional = jpaJobService.tryGetGeneratedJobData(guid);

                    if (jpaGeneratedDataOptional.isPresent()) {
                        return Optional.of(mapJpaJobGeneratedDataToJobData(jpaGeneratedDataOptional.get()));
                    }

                    return Optional.empty();
                }
        );
    }

    @Override
    public JobDataWithByteSink storeGeneratedData(
            String jobGuid,
            String useCode,
            String mediaTypeCode,
            JobDataEncoding encoding) throws IOException {
        Preconditions.checkArgument(StringUtils.isNotBlank(jobGuid));
        Preconditions.checkArgument(StringUtils.isNotBlank(mediaTypeCode));

        // simplify the media type code.
        final String simplifiedMediaTypeCode = MediaType.parse(mediaTypeCode).withoutParameters().toString();

        String guid = UUID.randomUUID().toString();
        JobData data = new JobData(guid, JobDataType.GENERATED, useCode, mediaTypeCode, encoding);
        JobDataWithByteSink result = new JobDataWithByteSink(data, dataStorageService.put(guid));

        contentedDataRetryTemplate.execute((RetryCallback<Object, DataIntegrityViolationException>) context -> {
            transactionTemplate.executeWithoutResult(
                    (transactionStatus) -> jpaJobService.ensureJobDataMediaType(simplifiedMediaTypeCode)
            );
            return Boolean.TRUE;
        });

        transactionTemplate.executeWithoutResult(
                (transactionStatus) -> jpaJobService.createGeneratedJobData(
                        jobGuid, guid, useCode, simplifiedMediaTypeCode, encoding.lowerName())
        );

        return result;
    }

    @Override
    public JobData storeSuppliedData(
            String useCode,
            String mediaTypeCode,
            JobDataEncoding encoding,
            ByteSource byteSource
    ) throws IOException {
        Preconditions.checkArgument(null != byteSource, "the byte source must be supplied to provide data");
        Preconditions.checkArgument(StringUtils.isNotBlank(mediaTypeCode));
        Preconditions.checkArgument(null != encoding, "the encoding must be supplied");

        final String simplifiedMediaTypeCode = MediaType.parse(mediaTypeCode).withoutParameters().toString();
        String guid = UUID.randomUUID().toString();
        JobData data;
        long len;

        try(InputStream inputStream = byteSource.openStream()) {

            // TODO; constrain this to a sensible size

            len = dataStorageService.put(guid).writeFrom(inputStream);
            data = new JobData(guid, JobDataType.SUPPLIED, useCode, simplifiedMediaTypeCode, encoding);

            transactionTemplate.executeWithoutResult(
                    (transactionStatus) ->
                            jpaJobService.createSuppliedJobData(
                                    guid,
                                    useCode,
                                    simplifiedMediaTypeCode,
                                    encoding.lowerName())
            );
        }

        LOGGER.info("did supply {}b job data; {}", len, data);

        return data;
    }

    @Override
    public Optional<JobDataWithByteSource> tryObtainData(String guid) throws IOException {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid must be supplied");

        // TODO (andponlin) make the data be specified either as generated or supplied.

        Optional<JobSuppliedData> jpaJobSuppliedDataOptional = transactionTemplate.execute(
                (transactionStatus) -> jpaJobService.tryGetSuppliedJobData(guid)
        );

        if (jpaJobSuppliedDataOptional.isPresent()) {
            String storageCode = jpaJobSuppliedDataOptional.get().getStorageCode();
            ByteSource byteSource = dataStorageService.get(storageCode)
                    .orElseThrow(() -> new JobServiceException("could not find the stored data with code [" + storageCode + "]"));

            return Optional.of(new JobDataWithByteSource(mapJpaJobSuppliedDataToJobData(jpaJobSuppliedDataOptional.get()), byteSource));
        }

        Optional<JobGeneratedData> jpaJobGeneratedDataOptional = transactionTemplate.execute(
                (transactionStatus) -> jpaJobService.tryGetGeneratedJobData(guid)
        );

        if (jpaJobGeneratedDataOptional.isPresent()) {
            String storageCode = jpaJobGeneratedDataOptional.get().getStorageCode();
            ByteSource byteSource = dataStorageService.get(storageCode)
                    .orElseThrow(() -> new JobServiceException("could not find the stored data with code [" + storageCode + "]"));

            return Optional.of(new JobDataWithByteSource(mapJpaJobGeneratedDataToJobData(jpaJobGeneratedDataOptional.get()), byteSource));
        }

        return Optional.empty();
    }

    private Class<? extends JobSpecification> getConcreteSpecificationClassForJobTypeCode(String jobTypeCode) {
        Preconditions.checkArgument(jobTypeCode != null, "the job type code is required");
        return jobRunners.stream()
                .filter(jr -> jr.getJobTypeCode().equals(jobTypeCode))
                .findFirst()
                .map(JobRunner::getSupportedSpecificationClass)
                .orElseThrow(() -> new IllegalStateException("unable to find a runner for [" + jobTypeCode + "]"));
    }

    /**
     * <p>Used for situations where data may be persisted to the database, but where there may be some contention
     * on storing that data. So there may be conflicts and it may be necessary to retry.</p>
     */
    private static RetryTemplate createContentedDataRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialRandomBackOffPolicy backOffPolicy = new ExponentialRandomBackOffPolicy();
        backOffPolicy.setInitialInterval(500L);
        backOffPolicy.setMaxInterval(10000L);

        MaxAttemptsRetryPolicy retryPolicy = new MaxAttemptsRetryPolicy();
        retryPolicy.setMaxAttempts(10);

        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(retryPolicy);

        return retryTemplate;
    }

    private static RetryTemplate createRunRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500L);
        backOffPolicy.setMaxInterval(30000L);

        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
        retryTemplate.setListeners(new RetryListener[]{
                new RetryListener() {
                    @Override
                    public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                        RetryListener.super.onError(context, callback, throwable);
                        LOGGER.error("failed to retry", throwable);
                    }
                }
        });

        return retryTemplate;
    }

    /**
     * <p>Runs in a loop taking work from the database queue table.</p>
     */
    private void loopAwaitingAndRunningAvailableJobs() {
        while (true) {
            try {
                workLock.lock();

                if (State.RUNNING != state()) {
                    return;
                }

                if (!runAvailableJobs()) {
                    if (!hasWork.await(15, TimeUnit.MINUTES)) {
                        LOGGER.debug("no jobs available");
                    }
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new JobServiceException("interrupted when checking for available jobs", ie);
            } finally {
                workLock.unlock();
            }
        }
    }

    /**
     * <p>Loops, obtaining work from the queue. Run the jobs until there are no more available and then
     * return.</p>
     * @return true if there was a job processed.
     */
    private boolean runAvailableJobs() {
        var result = transactionTemplate.execute((status) -> {

            // query for a job and skip lock any which are already being processed
            org.haiku.haikudepotserver.job.jpa.model.Job jpaJob;
            boolean didProcessAJob = false;

            while (null != (jpaJob = jpaJobService.tryGetNextAvailableJob().orElse(null))) {
                try {
                    JobSpecification jobSpecification = objectMapper.treeToValue(
                            jpaJob.getSpecification().getData(),
                            getConcreteSpecificationClassForJobTypeCode(jpaJob.getType().getCode())
                    );
                    runSpecificationInCurrentThread(jobSpecification);
                    didProcessAJob = true;
                } catch (JsonProcessingException jpe) {
                    throw new JobServiceException(
                            "unable to parse job specification for [%s]".formatted(jpaJob.getCode()),
                            jpe);
                }
            }

            return didProcessAJob;
        });

        return BooleanUtils.isTrue(result);
    }

    private void updateStateStatus(String code, JobSnapshot.Status updatedStatusEnum) {
        Instant now = clock.instant();

        transactionTemplate.executeWithoutResult(
                (status) -> jpaJobService.updateStateStatus(code, now, updatedStatusEnum)
        );
        LOGGER.info("job [{}] state to [{}]", code, updatedStatusEnum);
    }

    private Optional<JobRunner<? extends JobSpecification>> tryGetJobRunner(final String jobTypeCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobTypeCode));
        Preconditions.checkState(null!=jobRunners,"the job runners must be configured - was this started up properly?");
        return jobRunners.stream()
                .filter(j -> j.getJobTypeCode().equals(jobTypeCode))
                .collect(SingleCollector.optional());
    }

    /**
     * <p>This will actually run the job.  This does not need to be locked.</p>
     */

    private <T extends JobSpecification> void runSpecificationInCurrentThread(T specification) {
        Preconditions.checkArgument(null != specification, "the job specification must be supplied to run the job");
        Optional<JobRunner<?>> jobRunnerOptional = tryGetJobRunner(specification.getJobTypeCode());

        if (jobRunnerOptional.isEmpty()) {
            LOGGER.error(
                    "there is no job runner available for job type code [{}]; - failing",
                    specification.getJobTypeCode());

            setJobFailTimestamp(specification.getGuid());
        }

        JobRunner<T> jobRunner = (JobRunner<T>) jobRunnerOptional.get();

        try {
            updateStateStatus(specification.getGuid(), JobSnapshot.Status.STARTED);
            jobRunner.run(this, specification);
            updateStateStatus(specification.getGuid(), JobSnapshot.Status.FINISHED);
        }
        catch(Throwable th) {
            LOGGER.error(specification.getGuid() + "; failure to run the job", th);
            setJobFailTimestamp(specification.getGuid());
        }
    }

    /**
     * <p>Create a new Job in the database based on the specification.</p>
     * @return the code of the {@link org.haiku.haikudepotserver.job.jpa.model.Job}.
     */
    private String persistNewJobTransactionally(JobSpecification specification, boolean started) {
        Preconditions.checkNotNull(specification, "the specification must be supplied");

        if (null == specification.getGuid()) {
            specification.setGuid(UUID.randomUUID().toString());
        } else {
            if (jpaJobService.existsjob(specification.getGuid())) {
                throw new JobServiceException("the job with code [" + specification.getGuid() + "] already exists");
            }
        }

        Instant now = clock.instant();

        // work in a separate transaction because we want to "dirty update" the state.
        String jobCode = transactionTemplate.execute((transactionStatus) -> {

            jpaJobService.createJob(
                    specification.getGuid(),
                    specification.getJobTypeCode(),
                    specification.getOwnerUserNickname(),
                    now,
                    specification.tryGetTimeToLiveMillis().orElse(TTL_DEFAULT),
                    objectMapper.valueToTree(specification),
                    specification.getSuppliedDataGuids(),
                    started);

            return specification.getGuid();
        });

        LOGGER.info("did {} job [{}] of type [{}] with owner [{}]",
                started ? "start" : "queue",
                jobCode,
                specification.getJobTypeCode(),
                specification.getOwnerUserNickname()
        );

        return jobCode;
    }

    /**
     * Find any dangling running jobs. These are jobs that were started but the running process failed
     * and so the job still has the status of a started job even though it actually failed. Once found,
     * set them to having failed.
     */
    private void correctDanglingStartedJobs() {
        transactionTemplate.executeWithoutResult((transactionStatus) ->
                jpaJobService.getDanglingStartedJobCodes().forEach(
                        jobCode -> jpaJobService.updateStateStatus(jobCode, clock.instant(), JobSnapshot.Status.FAILED)
                )
        );
    }

    private Optional<Job> getMatchingJob(JobSpecification specification, Set<JobSnapshot.Status> statuses) {
        // if we're including any started jobs then we need to check that there's no started ones in the
        // database which are dangling as this may mess-up the queries.

        if (statuses.contains(JobSnapshot.Status.STARTED)) {
            correctDanglingStartedJobs();
        }

        // the stream will be in the most desirable ordering.
        return transactionTemplate.execute((transactionStatus) ->
                jpaJobService.streamJobsByTypeAndStatuses(specification.getJobTypeCode(), statuses)
                        .map(this::mapJpaJobToJob)
                        .filter(job -> job.getJobSpecification().isEquivalent(specification))
                        .findFirst()
        );
    }

    private JobData mapJpaJobSuppliedDataToJobData(JobSuppliedData jpaJobData) {
        return new JobData(
                jpaJobData.getCode(),
                JobDataType.SUPPLIED,
                jpaJobData.getUseCode(),
                jpaJobData.getMediaType().getCode(),
                JobDataEncoding.valueOf(jpaJobData.getEncoding().getCode().toUpperCase())
        );
    }

    private JobData mapJpaJobGeneratedDataToJobData(JobGeneratedData jpaJobData) {
        return new JobData(
                jpaJobData.getCode(),
                JobDataType.GENERATED,
                jpaJobData.getUseCode(),
                jpaJobData.getMediaType().getCode(),
                JobDataEncoding.valueOf(jpaJobData.getEncoding().getCode().toUpperCase())
        );
    }

    private java.util.Date toDateIfNotNull(Instant instant) {
        return Optional.ofNullable(instant).map(java.util.Date::from).orElse(null);
    }

    private Job mapJpaJobToJob(org.haiku.haikudepotserver.job.jpa.model.Job jpaJob) {
        JobState jpaJobState = jpaJob.getState();

        Job result = new Job();
        result.setStartTimestamp(toDateIfNotNull(jpaJobState.getStartTimestamp()));
        result.setFinishTimestamp(toDateIfNotNull(jpaJobState.getFinishTimestamp()));
        result.setQueuedTimestamp(toDateIfNotNull(jpaJobState.getQueueTimestamp()));
        result.setFailTimestamp(toDateIfNotNull(jpaJobState.getFailTimestamp()));
        result.setCancelTimestamp(toDateIfNotNull(jpaJobState.getCancelTimestamp()));
        result.setProgressPercent(jpaJobState.getProgressPercent());

        // TODO; pro-actively loading the job specification here is going to be an
        //  overhead that could be handled better; for now continue the same approach.

        JsonNode jobSpecificationNode = jpaJob.getSpecification().getData();

        try {
            result.setJobSpecification(objectMapper.treeToValue(
                    jobSpecificationNode,
                    getConcreteSpecificationClassForJobTypeCode(jpaJob.getType().getCode())
            ));
        } catch (JsonProcessingException jpe) {
            throw new JobServiceException("unable to process the job specification for job [" + jpaJob.getCode() + "]", jpe);
        }

        CollectionUtils.emptyIfNull(jpaJob.getState().getGeneratedDatas()).stream()
                .map(JobGeneratedData::getCode)
                .forEach(result::addGeneratedDataGuid);

        return result;
    }

}
