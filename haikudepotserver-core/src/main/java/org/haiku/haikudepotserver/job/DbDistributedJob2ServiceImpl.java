/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.Nullable;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.cayenne.CayenneRuntimeException;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.access.DataNode;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.tx.TransactionDescriptor;
import org.apache.cayenne.tx.TransactionPropagation;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.RandomStringGenerator;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.auto._JobData;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.backoff.ExponentialRandomBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.policy.MaxAttemptsRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>An instance of the {@link org.haiku.haikudepotserver.job.model.JobService} which
 * is distributed. It will coordinate with other instances through a database which
 * is accessed via Apache Cayenne + JDBC.</p>
 */
public class DbDistributedJob2ServiceImpl extends AbstractExecutionThreadService implements JobService {


    private static final Logger LOGGER = LoggerFactory.getLogger(DbDistributedJob2ServiceImpl.class);

    /**
     * <p>Cayenne {@link TransactionDescriptor} which will create a new transaction.</p>
     */

    private static final TransactionDescriptor CAY_TRANSACTION_DESCRIPTOR_NEW = TransactionDescriptor
            .builder()
            .propagation(TransactionPropagation.REQUIRES_NEW)
            .isolation(TransactionDescriptor.ISOLATION_DEFAULT)
            .build();

    private final static long TTL_DEFAULT = TimeUnit.HOURS.toMillis(2);

    /**
     * No job should still be around after this length of time. Maybe if jobs were stopped part way through by a
     * system failure then they may hang around in the database. This TTL will ensure that they too get purged.
     */
    private final static long TTL_MAX = TimeUnit.DAYS.toMillis(5);

    private final static long DELAY_CHECK_AWAIT_FINISHED_SECONDS = 2;

    private final static long DELAY_AWAIT_ADVISORY_LOCK_SECONDS = 60;

    private final static long DELAY_CHECK_JOBS_SECONDS = 60 * 5;

    private final ObjectMapper objectMapper;

    private final DataStorageService dataStorageService;

    private final ApplicationEventPublisher applicationEventPublisher;

    private final Clock clock;

    private final Collection<JobRunner<?>> jobRunners;

    private final ReentrantLock workLock = new ReentrantLock();
    private final Condition hasWork = workLock.newCondition();
    private final RetryTemplate contentedDataRetryTemplate = createContentedDataRetryTemplate();

    private final ServerRuntime serverRuntime;

    private final String name;

    public DbDistributedJob2ServiceImpl(
            ServerRuntime serverRuntime,
            ObjectMapper objectMapper,
            DataStorageService dataStorageService,
            Collection<JobRunner<?>> jobRunners,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        this.serverRuntime = serverRuntime;
        this.clock = Clock.systemUTC();
        this.objectMapper = objectMapper;
        this.dataStorageService = dataStorageService;
        this.jobRunners = jobRunners;
        this.applicationEventPublisher = applicationEventPublisher;
        this.name = createServiceName();
    }

    private static String createServiceName() {
        RandomStringGenerator generator = new RandomStringGenerator.Builder()
                .withinRange(new char[][] {{'a','z'}, {'0','9'}}).get();
        return String.format("%s-%s",
                DbDistributedJob2ServiceImpl.class.getSimpleName(),
                generator.generate(4)
        );
    }

    @Override
    protected String serviceName() {
        return this.name;
    }

    @PostConstruct
    public void init() {
        LOGGER.info("will start and await running [{}]", name);
        startAsync();
        awaitRunning();
        LOGGER.info("did start and await running [{}]", name);
        signalHasWork();
    }

    @PreDestroy
    public void tearDown() {
        LOGGER.info("will stop and await terminated [{}]", name);
        stopAsync();
        signalHasWork();
        awaitTerminated();
        LOGGER.info("did stop and await terminated [{}]", name);
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

        while (Duration.between(now, clock.instant()).toMillis() < timeout) {
            if (0 == DbDistributedJob2Helper.countNotFinished(serverRuntime.newContext())) {
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

        while (Duration.between(now, clock.instant()).toMillis() < timeout) {
            if (!DbDistributedJob2Helper.isFinished(serverRuntime.newContext(), guid)) {
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

        persistNewJob(specification, false); // not started
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

        persistNewJob(specification, true); // started
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

        return org.haiku.haikudepotserver.dataobjects.Job.tryGetByCode(serverRuntime.newContext(), guid)
                .map(this::mapPersistedJobToJob);
    }

    @Override
    public void removeJob(String guid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");
        ObjectContext objectContext = serverRuntime.newContext();
        DbDistributedJob2Helper.deleteJob(objectContext, guid);
        objectContext.commitChanges();
    }

    @Override
    public void setJobProgressPercent(String guid, Integer progressPercent) {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid is required");

        if (progressPercent == null || progressPercent < 0 || progressPercent > 100) {
            throw new IllegalArgumentException("progress percent must be between 0 and 100; [" + progressPercent + "]");
        }

        serverRuntime.performInTransaction(
                () -> {
                    ObjectContext objectContext = serverRuntime.newContext();

                    if (DbDistributedJob2Helper.setJobProgressPercent(objectContext, guid, progressPercent)) {
                        objectContext.commitChanges();
                        LOGGER.info("job [{}] progress to {}%", guid, progressPercent);
                    }

                    return Boolean.TRUE;
                },
                CAY_TRANSACTION_DESCRIPTOR_NEW
        );

    }

    @Override
    public void clearExpiredJobs() {

        DataNode dataNode = serverRuntime.getDataDomain().getDataNode("HaikuDepotServer");

        try (Connection connection = dataNode.getDataSource().getConnection()) {

            connection.setAutoCommit(false);

            // take out a global lock so that two `clearExpiredJobs` processes cannot happen at the same time.
            // see `runNextAvailableJob`.

            if (!DbDistributedJob2Helper.tryTransactionalAdvisoryLock(
                    connection,
                    false,
                    Duration.ofSeconds(DELAY_AWAIT_ADVISORY_LOCK_SECONDS))) {
                LOGGER.warn("was unable to acquire exclusive advisory lock for jobs during clear expire jobs process - abandoning");
                return;
            }

            // some started jobs may have been forcefully stopped (eg; JVM stopped) and the started job would be left
            // dangling. This will ensure that those jobs are marked as failed.

            {
                LOGGER.info("will correct dangling started jobs");
                correctDanglingStartedJobs();
                LOGGER.info("did correct dangling started jobs");
            }

            // clear any jobs which are completed and have naturally expired.
            {
                LOGGER.info("will clear completed expired jobs");
                long clearedCompletedExpiredJobs = DbDistributedJob2Helper.clearCompletedExpiredJobs(serverRuntime, clock.instant());
                LOGGER.info("did clear {} completed expired jobs", clearedCompletedExpiredJobs);
            }

            // Some jobs may be still in the database but have failed owing to system reasons. This will clean those
            // up as well.
            {
                LOGGER.info("will clear expired jobs");
                long clearedExpiredJobs = DbDistributedJob2Helper.clearExpiredJobs(serverRuntime, clock.instant().minus(TTL_MAX, ChronoUnit.MILLIS));
                LOGGER.info("did clear {} expired jobs", clearedExpiredJobs);
            }
        } catch (SQLException se) {
            throw new IllegalStateException("unable to clear expired jobs", se);
        }
    }

    @Override
    public List<? extends JobSnapshot> findJobs(
            @Nullable User user,
            @Nullable Set<JobSnapshot.Status> statuses,
            int offset,
            int limit) {
        Preconditions.checkArgument(offset >= 0, "bad offset");
        Preconditions.checkArgument(limit > 0, "bad limit");
        return DbDistributedJob2Helper.findJobs(
                        serverRuntime.newContext(),
                        Optional.ofNullable(user).map(User::getNickname).orElse(null),
                        statuses,
                        offset,
                        limit)
                .stream()
                .map(this::mapPersistedJobToJob)
                .toList();
    }

    @Override
    public int totalJobs(
            @Nullable User user,
            @Nullable Set<JobSnapshot.Status> statuses) {
        return (int) DbDistributedJob2Helper.totalJobs(
                serverRuntime.newContext(),
                        Optional.ofNullable(user).map(User::getNickname).orElse(null),
                        statuses);
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid) {
        Preconditions.checkArgument(StringUtils.isNotBlank(jobDataGuid), "the data guid is required");
        ObjectContext context = serverRuntime.newContext();
        return org.haiku.haikudepotserver.dataobjects.JobData.tryGetByCode(context, jobDataGuid)
                .map(_JobData::getJob)
                .map(this::mapPersistedJobToJob);
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
        ObjectContext context = serverRuntime.newContext();
        return org.haiku.haikudepotserver.dataobjects.JobData.tryGetByCode(context, guid)
                .map(this::mapPersistedJobDataToJobData);
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

        // make sure tha the media type is already persisted.

        contentedDataRetryTemplate.execute((RetryCallback<Object, CayenneRuntimeException>) context -> {
            ObjectContext objectContext = serverRuntime.newContext();
            DbDistributedJob2Helper.ensureJobDataMediaType(objectContext, simplifiedMediaTypeCode);
            objectContext.commitChanges();
            return true;
        });

        serverRuntime.performInTransaction(
                () -> {
                    ObjectContext objectContext = serverRuntime.newContext();
                    DbDistributedJob2Helper.createGeneratedJobData(
                            objectContext, jobGuid, guid, useCode, simplifiedMediaTypeCode, encoding.lowerName());
                    objectContext.commitChanges();
                    return Boolean.TRUE;
                },
                CAY_TRANSACTION_DESCRIPTOR_NEW
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

            serverRuntime.performInTransaction(
                    () -> {
                        ObjectContext objectContext = serverRuntime.newContext();

                        DbDistributedJob2Helper.createSuppliedJobData(
                                objectContext,
                                guid,
                                useCode,
                                simplifiedMediaTypeCode,
                                encoding.lowerName()
                        );

                        objectContext.commitChanges();

                        return Boolean.TRUE;
                    },
                    CAY_TRANSACTION_DESCRIPTOR_NEW
            );
        }

        LOGGER.info("did supply {}b job data; {}", len, data);

        return data;
    }

    @Override
    public Optional<JobDataWithByteSource> tryObtainData(String guid) throws IOException {
        Preconditions.checkArgument(StringUtils.isNotBlank(guid), "the guid must be supplied");

        ObjectContext objectContext = serverRuntime.newContext();
        Optional<org.haiku.haikudepotserver.dataobjects.JobData> persistedJobSuppliedDataOptional
                = org.haiku.haikudepotserver.dataobjects.JobData.tryGetByCode(objectContext, guid);

        if (persistedJobSuppliedDataOptional.isPresent()) {
            String storageCode = persistedJobSuppliedDataOptional.get().getStorageCode();
            ByteSource byteSource = dataStorageService.get(storageCode)
                    .orElseThrow(() -> new JobServiceException("could not find the stored data with code [" + storageCode + "]"));

            return Optional.of(new JobDataWithByteSource(
                    mapPersistedJobDataToJobData(persistedJobSuppliedDataOptional.get()),
                    byteSource));
        }

        return Optional.empty();
    }

    private Optional<Class<? extends JobSpecification>> tryGetConcreteSpecificationClassForJobTypeCode(String jobTypeCode) {
        Preconditions.checkArgument(jobTypeCode != null, "the job type code is required");
        return jobRunners.stream()
                .filter(jr -> jr.getJobTypeCode().equals(jobTypeCode))
                .findFirst()
                .map(JobRunner::getSupportedSpecificationClass);
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

                if (Service.State.RUNNING != state()) {
                    return;
                }

                if (!runNextAvailableJob()) {
                    if (!hasWork.await(DELAY_CHECK_JOBS_SECONDS, TimeUnit.SECONDS)) {
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
     * <p>Obtains work from the queue and runs it.</p>
     * @return true if there was a job processed.
     */
    private boolean runNextAvailableJob() {

        DataNode dataNode = serverRuntime.getDataDomain().getDataNode("HaikuDepotServer");

        try (Connection connection = dataNode.getDataSource().getConnection()) {

            connection.setAutoCommit(false);

            // Try take a shared lock for the job execution. If this is not possible then log it and return. It will
            // try again after some delay or if a new job comes in. This arrangement coordinates with the job garbage
            // collection system which has to take out an exclusive lock to clean the database.

            if (!DbDistributedJob2Helper.tryTransactionalAdvisoryLock(
                    connection,
                    true,
                    Duration.ofSeconds(DELAY_AWAIT_ADVISORY_LOCK_SECONDS))) {
                LOGGER.warn("was unable to acquire shared advisory lock for jobs during run next available job");
                return false;
            }

            // query for a job and skip lock any which are already being processed
            Optional<String> jobCodeOptional = DbDistributedJob2Helper.tryGetNextAvailableJobCode(connection);

            if (jobCodeOptional.isEmpty()) {
                return false;
            }

            String jobCode = jobCodeOptional.get();

            // this `objectContext` is not on the same Connection.
            ObjectContext objectContext = serverRuntime.newContext();
            org.haiku.haikudepotserver.dataobjects.Job persistedJob
                    = org.haiku.haikudepotserver.dataobjects.Job.getByCode(objectContext, jobCode);
            String jobTypeCode = persistedJob.getJobType().getCode();

            Optional<Class<? extends JobSpecification>> jobSpecificationClassOptional
                    = tryGetConcreteSpecificationClassForJobTypeCode(persistedJob.getJobType().getCode());

            if (jobSpecificationClassOptional.isEmpty()) {
                LOGGER.error("unable to find the job specification of type [{}] for job [{}] - will fail", jobTypeCode, jobCode);
                setJobFailTimestamp(persistedJob.getCode());
                return true; // effectively the logic did do something.
            }

            JobSpecification jobSpecification;

            try {
                jobSpecification = objectMapper.readValue(
                        persistedJob.getSpecification(),
                        jobSpecificationClassOptional.get()
                );
            } catch (JsonProcessingException jpe) {
                LOGGER.error("unable to parse the job specification of type [{}] for job [{}] - will fail", jobTypeCode, jobCode, jpe);
                setJobFailTimestamp(jobCode);
                return true; // effectively the logic did do something.
            }

            runSpecificationInCurrentThread(jobSpecification);
            return true;
        } catch (SQLException se) {
            throw new JobServiceException("unable to run next available job", se);
        }
    }

    private void updateStateStatus(String code, JobSnapshot.Status updatedStatusEnum) {
        Instant now = clock.instant();

        serverRuntime.performInTransaction(
                () -> {
                    ObjectContext objectContext = serverRuntime.newContext();
                    DbDistributedJob2Helper.updateJobStatus(objectContext, code, now, updatedStatusEnum);
                    objectContext.commitChanges();
                    LOGGER.info("job [{}] state to [{}]", code, updatedStatusEnum);
                    return Boolean.TRUE;
                },
                CAY_TRANSACTION_DESCRIPTOR_NEW
        );
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
    private String persistNewJob(JobSpecification specification, boolean started) {
        Preconditions.checkNotNull(specification, "the specification must be supplied");

        if (null == specification.getGuid()) {
            specification.setGuid(UUID.randomUUID().toString());
        } else {
            if (org.haiku.haikudepotserver.dataobjects.Job.tryGetByCode(
                    serverRuntime.newContext(),
                    specification.getGuid()
            ).isPresent()) {
                throw new JobServiceException("the job with code [" + specification.getGuid() + "] already exists");
            }
        }

        Instant now = clock.instant();

        ObjectContext objectContext = serverRuntime.newContext();

        DbDistributedJob2Helper.createJob(
                objectContext,
                specification.getGuid(),
                specification.getJobTypeCode(),
                specification.getOwnerUserNickname(),
                now,
                specification.tryGetTimeToLiveMillis().orElse(TTL_DEFAULT),
                objectMapper.valueToTree(specification),
                specification.getSuppliedDataGuids(),
                started);

        objectContext.commitChanges();

        LOGGER.info("did {} job [{}] of type [{}] with owner [{}]",
                started ? "start" : "queue",
                specification.getGuid(),
                specification.getJobTypeCode(),
                specification.getOwnerUserNickname()
        );

        return specification.getGuid();
    }

    /**
     * Find any dangling running jobs. These are jobs that were started but the running process failed
     * and so the job still has the status of a started job even though it actually failed. Once found,
     * set them to having failed.
     */
    private void correctDanglingStartedJobs() {
        DataNode dataNode = serverRuntime.getDataDomain().getDataNode("HaikuDepotServer");
        Set<String> jobCodes;

        try (Connection connection = dataNode.getDataSource().getConnection()) {
            jobCodes = DbDistributedJob2Helper.getDanglingStartedJobCodes(connection);
        } catch (SQLException se) {
            throw new JobServiceException("unable to get dangling started job codes", se);
        }

        ObjectContext objectContext = serverRuntime.newContext();

        jobCodes.forEach(
                jobCode -> DbDistributedJob2Helper.updateJobStatus(
                        objectContext,
                        jobCode,
                        clock.instant(),
                        JobSnapshot.Status.FAILED
                )
        );

        objectContext.commitChanges();
    }

    private Optional<Job> getMatchingJob(JobSpecification specification, Set<JobSnapshot.Status> statuses) {
        // if we're including any started jobs then we need to check that there's no started ones in the
        // database which are dangling as this may mess-up the queries.

        if (statuses.contains(JobSnapshot.Status.STARTED)) {
            correctDanglingStartedJobs();
        }

        // the stream will be in the most desirable ordering.
        return DbDistributedJob2Helper.streamJobsByTypeAndStatuses(serverRuntime.newContext(), specification.getJobTypeCode(), statuses)
                .map(this::mapPersistedJobToJob)
                .filter(job -> job.getJobSpecification().isEquivalent(specification))
                .findFirst();
    }

    private JobData mapPersistedJobDataToJobData(org.haiku.haikudepotserver.dataobjects.JobData jpaJobData) {
        return new JobData(
                jpaJobData.getCode(),
                JobDataType.GENERATED,
                jpaJobData.getUseCode(),
                jpaJobData.getJobDataMediaType().getCode(),
                JobDataEncoding.valueOf(jpaJobData.getJobDataEncoding().getCode().toUpperCase())
        );
    }

    private java.util.Date toDateIfNotNull(LocalDateTime localDateTime) {
        return Optional.ofNullable(localDateTime)
                .map(ldt -> ldt.toInstant(ZoneOffset.UTC))
                .map(java.util.Date::from)
                .orElse(null);
    }

    private Job mapPersistedJobToJob(org.haiku.haikudepotserver.dataobjects.Job persistedJob) {
        Job result = new Job();

        result.setStartTimestamp(persistedJob.getStartTimestamp());
        result.setFinishTimestamp(persistedJob.getFinishTimestamp());
        result.setQueuedTimestamp(persistedJob.getQueueTimestamp());
        result.setFailTimestamp(persistedJob.getFailTimestamp());
        result.setCancelTimestamp(persistedJob.getCancelTimestamp());
        result.setProgressPercent(persistedJob.getProgressPercent());

        // TODO; pro-actively loading the job specification here is going to be an
        //  overhead that could be handled better; for now continue the same approach.

        try {
            String jobTypeCode = persistedJob.getJobType().getCode();
            Class<? extends JobSpecification> specificationClass = tryGetConcreteSpecificationClassForJobTypeCode(jobTypeCode).orElse(null);

            if (null == specificationClass) {
                LOGGER.error("unable to find a specification class for job type code [{}}] on job [{}]", jobTypeCode, persistedJob.getCode());

                // add a fake job specification just so that the job can be seen in the API / GUI
                DbDistributedJob2ServiceImpl.PlaceboJobSpecification placeboJobSpecification = new DbDistributedJob2ServiceImpl.PlaceboJobSpecification();
                placeboJobSpecification.setGuid(persistedJob.getCode());
                result.setJobSpecification(placeboJobSpecification);
            } else {
                result.setJobSpecification(objectMapper.readValue(
                        persistedJob.getSpecification(),
                        specificationClass)
                );
            }
        } catch (JsonProcessingException jpe) {
            throw new JobServiceException("unable to process the job specification for job [" + persistedJob.getCode() + "]", jpe);
        }

        String codeGenerated = org.haiku.haikudepotserver.dataobjects.JobDataType.CODE_GENERATED;
        CollectionUtils.emptyIfNull(persistedJob.getJobDatas()).stream()
                .filter(jd -> jd.getJobDataType().getCode().equals(codeGenerated))
                .map(_JobData::getCode)
                .forEach(result::addGeneratedDataGuid);

        return result;
    }

    /**
     * <p>This is used in rare situations where the job specification is broken or is missing.</p>
     */
    private final static class PlaceboJobSpecification extends AbstractJobSpecification {
    }


}
