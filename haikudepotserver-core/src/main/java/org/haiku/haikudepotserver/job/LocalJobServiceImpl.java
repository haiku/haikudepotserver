/*
 * Copyright 2018-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import com.google.common.base.Preconditions;
import com.google.common.base.Stopwatch;
import com.google.common.base.Strings;
import com.google.common.collect.*;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.AbstractService;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * <p>This concrete implementation of the {@link JobService}
 * is able to run jobs locally in the virtual machine; it does not distribute or coordinate the run-state of the
 * jobs across virtual machines etc...</p>
 */

public class LocalJobServiceImpl
        extends AbstractService
        implements JobService, ApplicationContextAware {

    protected static Logger LOGGER = LoggerFactory.getLogger(JobService.class);

    private final static int SIZE_QUEUE = 10;

    private final static long TTL_DEFAULT = TimeUnit.HOURS.toMillis(2);

    private final DataStorageService dataStorageService;

    /**
     * <p>This gets setup through an query into the {@link ApplicationContext}.
     * </p>
     */

    private Collection<JobRunner> jobRunners = null;

    private ThreadPoolExecutor executor = null;

    private ApplicationContext applicationContext;

    private final ArrayBlockingQueue<Runnable> runnables = Queues.newArrayBlockingQueue(SIZE_QUEUE);

    /**
     * <p>Contains a mapping from the GUID to the job.</p>
     */

    private Map<String, Job> jobs;

    /**
     * <p>Job data that the system knows about.</p>
     */

    private final Set<JobData> datas = Sets.newHashSet();

    public LocalJobServiceImpl(
            DataStorageService dataStorageService) {
        this.dataStorageService = dataStorageService;
    }

    public LocalJobServiceImpl(
            DataStorageService dataStorageService,
            Collection<JobRunner> jobRunners) {
        this.dataStorageService = dataStorageService;
        this.jobRunners = jobRunners;
    }

    @PostConstruct
    public void init() {
        if (null == jobRunners) {
            jobRunners = applicationContext.getBeansOfType(JobRunner.class).values();
            LOGGER.info("configured {} job runners from the application context",
                    jobRunners.size());
        } else {
            LOGGER.info("{} job runners were already configured - will not "
                    + "populate any from the application context",
                    jobRunners.size());
        }
        startAsyncAndAwaitRunning();
    }

    @PreDestroy
    public void tearDown() {
        stopAsyncAndAwaitTerminated();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    // ------------------------------
    // RUN JOBS

    @Override
    public boolean awaitAllJobsFinishedUninterruptibly(long timeout) {
        Preconditions.checkArgument(timeout > 0);
        Stopwatch stopwatch = Stopwatch.createStarted();
        EnumSet<JobSnapshot.Status> earlyStatuses = EnumSet.of(JobSnapshot.Status.QUEUED, JobSnapshot.Status.STARTED);

        while (stopwatch.elapsed(TimeUnit.MILLISECONDS) < timeout
            && !filteredInternalJobs(null, earlyStatuses).isEmpty()) {
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        }

        return filteredInternalJobs(null, earlyStatuses).isEmpty();
    }

    @Override
    public boolean awaitJobFinishedUninterruptibly(String guid, long timeout) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid), "a guid must be supplied");
        Preconditions.checkArgument(timeout > 0);
        Stopwatch stopwatch = Stopwatch.createStarted();
        EnumSet<JobSnapshot.Status> earlyStatuses = EnumSet.of(JobSnapshot.Status.QUEUED, JobSnapshot.Status.STARTED);

        while(stopwatch.elapsed(TimeUnit.MILLISECONDS) < timeout
                && tryGetJob(guid).filter((j) -> earlyStatuses.contains(j.getStatus())).isPresent()) {
            Uninterruptibles.sleepUninterruptibly(1, TimeUnit.SECONDS);
        }

        return tryGetJob(guid).filter((j) -> earlyStatuses.contains(j.getStatus())).isPresent();
    }

    private Optional<JobRunner> getJobRunner(final String jobTypeCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobTypeCode));
        Preconditions.checkState(null!=jobRunners,"the job runners must be configured - was this started up properly?");
        return jobRunners.stream().filter(j -> j.getJobTypeCode().equals(jobTypeCode)).collect(SingleCollector.optional());
    }

    @Override
    public String submit(
            JobSpecification specification,
            Set<JobSnapshot.Status> coalesceForStatuses) {
        return validateAndCoalesceOrCreateJob(specification,
                coalesceForStatuses, this::createInternalJobBySubmittingToExecutor);
    }

    @Override
    public String immediate(
            JobSpecification specification, boolean coalesceFinished) {
        return validateAndCoalesceOrCreateJob(specification,
                EnumSet.of(JobSnapshot.Status.FINISHED),
                this::createInternalJobByRunningInCurrentThread);
    }

    private String validateAndCoalesceOrCreateJob(
            JobSpecification specification,
            Set<JobSnapshot.Status> coalesceForStatuses,
            Function<JobSpecification, Job> createJobFunction) {

        Preconditions.checkState(null != executor,
                "the executor has not been configured; was this service started correctly?");
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != coalesceForStatuses,
                "the statuses over which coalescing should occur must be supplied");

        getJobRunner(specification.getJobTypeCode()).orElseThrow(() ->
                new IllegalStateException("unable to run a job runner for the job type code; "
                        + specification.getJobTypeCode()));

        for (String guid : specification.getSuppliedDataGuids()) {
            if (!tryGetData(guid).isPresent()) {
                throw new IllegalStateException(
                        "unable to run a job specification because the specified data "
                                + guid + " was not able to be found");
            }
        }

        if (null==specification.getGuid()) {
            specification.setGuid(UUID.randomUUID().toString());
        } else {
            synchronized (this) {
               if (tryGetJob(specification.getGuid()).isPresent()) {
                   throw new IllegalStateException(
                           "a specification has been submitted for which there is already a job running; "
                                   + specification.getGuid());
               }
            }
        }

        // first clear out any expired jobs

        clearExpiredInternalJobs();

        // if there is an existing report that can be used then use it; otherwise make a new one.
        // The use of sorting below is to get the best job to re-use (the most recent) from all
        // of the possible ones.

        Optional<String> firstMatchingJobGuidOptional;

        synchronized (this) {
            firstMatchingJobGuidOptional = ImmutableList.copyOf(jobs.values())
                    .stream()
                    .filter((j) -> coalesceForStatuses.contains(j.getStatus()))
                    .filter((j) -> specification.isEquivalent(j.getJobSpecification()))
                    .sorted((j1, j2) -> ComparisonChain.start()
                            .compare(j1.getStatus(), j2.getStatus(),
                                    Ordering.explicit(
                                            JobSnapshot.Status.FINISHED,
                                            JobSnapshot.Status.STARTED,
                                            JobSnapshot.Status.QUEUED
                                    )
                            )
                            .compare(j1.getFinishTimestamp(), j2.getFinishTimestamp(), Ordering.natural().nullsLast())
                            .compare(j1.getStartTimestamp(), j2.getStartTimestamp(), Ordering.natural().nullsLast())
                            .compare(j1.getQueuedTimestamp(), j2.getQueuedTimestamp(), Ordering.natural().nullsLast())
                            .compare(j1.getFailTimestamp(), j2.getFailTimestamp(), Ordering.natural().nullsLast())
                            .compare(j1.getCancelTimestamp(), j2.getCancelTimestamp(), Ordering.natural().nullsLast())
                            .result()
                    )
                    .map(Job::getGuid)
                    .findFirst();
        }

        return firstMatchingJobGuidOptional.orElseGet(() -> createJobFunction.apply(specification).getGuid());
    }

    private Job createInternalJobBySubmittingToExecutor(final JobSpecification specification) {
        Job job = new Job(specification);

        LOGGER.debug("{}; will submit job", specification.toString());
        jobs.put(job.getGuid(), job);
        setInternalJobRunQueuedTimestamp(specification.getGuid());
        executor.submit(() -> {
            String threadNamePrior = Thread.currentThread().getName();

            try {
                Thread.currentThread().setName("job-run-" + StringUtils.abbreviate(specification.getGuid(), 4));
                runSpecificationInCurrentThread(specification);
            }
            finally {
                Thread.currentThread().setName(threadNamePrior);
            }
        });

        return job;
    }

    private Job createInternalJobByRunningInCurrentThread(JobSpecification specification) {
        Job job = new Job(specification);

        LOGGER.debug("{}; will run job immediately", specification.toString());
        jobs.put(job.getGuid(), job);
        setInternalJobRunQueuedTimestamp(specification.getGuid());
        runSpecificationInCurrentThread(specification);
        LOGGER.debug("{}; did run job immediately", specification.toString());

        return job;
    }

    /**
     * <p>This will actually run the job.  This does not need to be locked.</p>
     */

    private void runSpecificationInCurrentThread(JobSpecification specification) {
        Preconditions.checkArgument(null != specification, "the job specification must be supplied to run the job");
        Optional<JobRunner> jobRunnerOptional = getJobRunner(specification.getJobTypeCode());

        if(!jobRunnerOptional.isPresent()) {
            LOGGER.error(
                    "{}; there is no job runner available for job type code '{}'; - failing",
                    specification.toString(),
                    specification.getJobTypeCode());

            setJobFailTimestamp(specification.getGuid());
        }

        try {
            setInternalJobStartTimestamp(specification.getGuid());
            //noinspection unchecked
            jobRunnerOptional.get().run(this, specification);
            setInternalJobFinishTimestamp(specification.getGuid());
        }
        catch(Throwable th) {
            LOGGER.error(specification.getGuid() + "; failure to run the job", th);
            setJobFailTimestamp(specification.getGuid());
        }
    }

    // ------------------------------
    // PURGE

    private synchronized Optional<Job> tryFindInternalJobOwningJobData(final JobData data) {
        Preconditions.checkArgument(null != data, "the data must be provided");
        return jobs
                .values()
                .stream()
                .filter(v -> v.getDataGuids().contains(data.getGuid()))
                .collect(SingleCollector.optional());
    }

    /**
     * <p>Job data may be stored ready to be attached to a job.  If the attachment never
     * happens then the job data may hang around indefinitely.  This clearing process will
     * cull those job datas that have not been used before time reasonable timeout.</p>
     */

    private synchronized void clearExpiredDatas() {
        ImmutableList.copyOf(datas)
                .stream()
                .filter((d) -> System.currentTimeMillis() - d.getCreateTimestamp().getTime() > TTL_DEFAULT)
                .filter((d) -> !tryFindInternalJobOwningJobData(d).isPresent())
                .forEach((d) -> {
                    if(dataStorageService.remove(d.getGuid())) {
                        LOGGER.info("did delete the expired unassociated job data; [{}]", d);
                        datas.remove(d);
                    }
                    else {
                        LOGGER.error("was not able to delete the expired unassociated job data; [{}] - data will remain in situ", d);
                    }
                });
    }

    /**
     * <p>Removes the job and any data associated with the job.</p>
     */

    private synchronized void removeInternalJob(Job job) {
        Preconditions.checkArgument(null!=job, "the job must be supplied to remove a job");

        for(String guid : job.getDataGuids()) {
            Optional<JobData> jobDataOptional = tryGetData(guid);

            if(jobDataOptional.isPresent()) {
                if (dataStorageService.remove(guid)) {
                    LOGGER.info("did delete the job data; {}", guid);
                    datas.remove(jobDataOptional.get());
                } else {
                    LOGGER.error("was not able to delete the job data; {} - data will remain in situ", guid);
                }
            }
            else {
                LOGGER.info("the orchestration service does not know about the job data; {} - data will remain in situ", guid);
            }
        }

        jobs.remove(job.getGuid());
    }

    @Override
    public void clearExpiredJobs() {
        clearExpiredInternalJobs();
    }

    private synchronized void clearExpiredInternalJobs() {
        long nowMillis = System.currentTimeMillis();

        synchronized (this) {

            for (Job job : ImmutableList.copyOf(jobs.values())) {

                Long ttl = job.tryGetTimeToLiveMillis().orElse(TTL_DEFAULT);
                Date quiesenceTimestamp = null;

                switch (job.getStatus()) {

                    case CANCELLED:
                        quiesenceTimestamp = job.getCancelTimestamp();
                        break;

                    case FINISHED:
                        quiesenceTimestamp = job.getFinishTimestamp();
                        break;

                    case FAILED:
                        quiesenceTimestamp = job.getFailTimestamp();
                        break;

                }

                if (null != quiesenceTimestamp) {
                    if (nowMillis - quiesenceTimestamp.getTime() > ttl) {

                        removeInternalJob(job);

                        LOGGER.info(
                                "{} purged expired job for ttl; {}ms",
                                job.getJobSpecification().toString(),
                                ttl);
                    }
                }

            }
        }

        clearExpiredDatas();
    }

    // ------------------------------
    // GET JOBS / LIST / SEARCH

    private synchronized List<Job> filteredInternalJobs(
            final User user,
            final Set<JobSnapshot.Status> statuses) {

        clearExpiredInternalJobs();

        if (CollectionUtils.isEmpty(statuses)) {
            return Collections.emptyList();
        }

        return jobs
                .values()
                .stream()
                .filter(v -> null == user || user.getNickname().equals(v.getOwnerUserNickname()))
                .filter(v -> statuses.contains(v.getStatus()))
                .collect(Collectors.toList());

    }

    @Override
    public List<? extends JobSnapshot> findJobs(
            final User user,
            final Set<JobSnapshot.Status> statuses,
            int offset,
            int limit) {

        Preconditions.checkArgument(offset >= 0, "illegal offset value");
        Preconditions.checkArgument(limit >= 1, "illegal limit value");

        if(null!=statuses && statuses.isEmpty()) {
            return Collections.emptyList();
        }

        List<Job> result = filteredInternalJobs(user, statuses);

        if(offset >= result.size()) {
            return Collections.emptyList();
        }

        Collections.sort(result);

        if(offset + limit > result.size()) {
            limit = result.size() - offset;
        }

        return result.subList(offset, offset+limit);
    }

    @Override
    public int totalJobs(User user, Set<JobSnapshot.Status> statuses) {
        return filteredInternalJobs(user, statuses).size();
    }

    /**
     * <p>Note that this method will return a <em>copy</em> of the job and not the internal
     * representation of the job itself.</p>
     */

    @Override
    public synchronized Optional<? extends JobSnapshot> tryGetJob(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        JobSnapshot job = jobs.get(guid);

        if (null != job) {
            return Optional.of(new Job(job));
        }

        return Optional.empty();
    }

    @Override
    public synchronized void removeJob(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        Job job = jobs.get(guid);

        if (null != job) {
            removeInternalJob(job);
        }
    }

    /**
     * <p>Note that this method does not return a copy of the
     * {@link JobSnapshot}; it will return
     * the working copy.</p>
     */

    private Job getInternalJob(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        Job job = jobs.get(guid);

        if(null==job) {
            throw new IllegalStateException("no job run state exists for; " + guid);
        }

        return job;
    }

    // ------------------------------
    // SET STATUS

    @Override
    public synchronized void setJobProgressPercent(String guid, Integer progressPercent) {
        setInternalJobProgressPercent(guid, progressPercent);
    }

    @Override
    public synchronized void setJobFailTimestamp(String guid) {
        setInternalJobFailTimestamp(guid);
    }

    @Override
    public synchronized void setJobCancelTimestamp(String guid) {
        setInternalJobCancelTimestamp(guid);
    }

    private synchronized void setInternalJobStartTimestamp(String guid) {
        Job job = getInternalJob(guid);

        if(job.getStatus() != JobSnapshot.Status.QUEUED) {
            throw new IllegalStateException("it is not possible to start a job from status; " + job.getStatus());
        }

        job.setStartTimestamp();
        LOGGER.info("{}; start", job.getJobSpecification().toString());

        notifyAll();
    }

    private synchronized void setInternalJobFinishTimestamp(String guid) {
        Job job = getInternalJob(guid);

        if(job.getStatus() != JobSnapshot.Status.STARTED) {
            throw new IllegalStateException("it is not possible to finish a job from status; " + job.getStatus());
        }

        job.setFinishTimestamp();
        LOGGER.info("{}; finish", job.getJobSpecification().toString());

        notifyAll();
    }

    private synchronized void setInternalJobRunQueuedTimestamp(String guid) {
        Job job = getInternalJob(guid);

        if(job.getStatus() != JobSnapshot.Status.INDETERMINATE) {
            throw new IllegalStateException("it is not possible to queue a job from status; " + job.getStatus());
        }

        job.setQueuedTimestamp();
        LOGGER.info("{}; queued", job.getJobSpecification().toString());

        notifyAll();
    }

    private synchronized void setInternalJobFailTimestamp(String guid) {
        Job job = getInternalJob(guid);

        if(job.getStatus() != JobSnapshot.Status.STARTED) {
            throw new IllegalStateException("it is not possible to fail a job from status; " + job.getStatus());
        }

        job.setFailTimestamp();
        LOGGER.info("{}; fail", job.getJobSpecification().toString());

        notifyAll();
    }

    private synchronized void setInternalJobCancelTimestamp(String guid) {
        Job job = getInternalJob(guid);

        switch (job.getStatus()) {
            case QUEUED:
            case STARTED:
                job.setCancelTimestamp();
                LOGGER.info("{}; cancelled", job.getJobSpecification().toString());
                notifyAll();
                break;

            default:
                throw new IllegalStateException("it is not possible to cancel a job from status; " + job.getStatus());
        }
    }

    private synchronized void setInternalJobProgressPercent(String guid, Integer progressPercent) {
        Preconditions.checkArgument(null==progressPercent || (progressPercent >= 0 && progressPercent <= 100), "bad progress percent value");
        Job job = getInternalJob(guid);

        if(job.getStatus() != JobSnapshot.Status.STARTED) {
            throw new IllegalStateException("it is not possible to set the progress percent for a job from status; " + job.getStatus());
        }

        Integer priorProgressPercent = job.getProgressPercent();

        if(null!=progressPercent) {
            if(null==priorProgressPercent || priorProgressPercent.intValue() != progressPercent.intValue()) {
                LOGGER.info("{}; progress {}%", job.getJobSpecification().toString(), progressPercent);
            }
        }

        job.setProgressPercent(progressPercent);

        notifyAll();
    }

    // ------------------------------
    // SERVICE START / STOP LIFECYCLE

    @Override
    public void doStart() {
        try {
            Preconditions.checkState(null == executor);

            LOGGER.info("will start service");

            jobs = Maps.newHashMap();

            executor = new ThreadPoolExecutor(
                    0, // core pool size
                    1, // max pool size
                    1L, // time to shutdown threads
                    TimeUnit.MINUTES,
                    runnables,
                    new ThreadPoolExecutor.AbortPolicy());

            notifyStarted();

            LOGGER.info("did start service");
        }
        catch(Throwable th) {
            notifyFailed(th);
        }
    }

    @Override
    public void doStop() {
        try {
            Preconditions.checkNotNull(executor);

            LOGGER.info("will stop service");

            executor.shutdown();
            executor.awaitTermination(2, TimeUnit.MINUTES);
            executor = null;
            notifyStopped();

            jobs = null;

            LOGGER.info("did stop service");
        }
        catch(Throwable th) {
            notifyFailed(th);
        }
    }

    public void startAsyncAndAwaitRunning() {
        tryClearDataStorageService();
        startAsync();
        awaitRunning();
    }

    public void stopAsyncAndAwaitTerminated() {
        tryClearDataStorageService();
        stopAsync();
        awaitTerminated();
    }

    // ------------------------------
    // DATA INPUT AND OUTPUT

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobDataGuid));
        Optional<JobData> jobDataOptional = tryGetData(jobDataGuid);

        if(jobDataOptional.isPresent()) {
            return tryFindInternalJobOwningJobData(jobDataOptional.get());
        }

        return Optional.empty();
    }

    @Override
    public String deriveDataFilename(String jobDataGuid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobDataGuid));

        String descriptor = "jobdata";
        String extension = "dat";

        Optional<JobData> jobDataOptional = tryGetData(jobDataGuid);

        if(jobDataOptional.isPresent()) {

            JobData jobData = jobDataOptional.get();
            Optional<? extends JobSnapshot> jobOptional = tryGetJobForData(jobDataGuid);

            if(jobOptional.isPresent()) {
               descriptor = jobOptional.get().getJobTypeCode();
            }

            // TODO; get the extensions from a file etc...
            if (!Strings.isNullOrEmpty(jobData.getMediaTypeCode())) {
                if (jobData.getMediaTypeCode().startsWith(MediaType.CSV_UTF_8.withoutParameters().toString())) {
                    extension = "csv";
                }

                if(jobData.getMediaTypeCode().equals(MediaType.ZIP.withoutParameters().toString())) {
                    extension = "zip";
                }

                if(jobData.getMediaTypeCode().equals(MediaType.TAR.withoutParameters().toString())) {
                    extension = "tgz";
                }

                if(jobData.getMediaTypeCode().equals(MediaType.PLAIN_TEXT_UTF_8.withoutParameters().toString())) {
                    extension = "txt";
                }
            }
        }

        return String.format(
                "hds_%s_%s_%s.%s",
                descriptor,
                DateTimeHelper.create14DigitDateTimeFormat().format(Instant.now()),
                jobDataGuid.substring(0,4),
                extension);
    }

    @Override
    public synchronized Optional<JobData> tryGetData(final String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid), "the guid must be supplied");
        return datas.stream().filter(d -> d.getGuid().equals(guid)).collect(SingleCollector.optional());
    }

    @Override
    public JobDataWithByteSink storeGeneratedData(
            String jobGuid,
            String useCode,
            String mediaTypeCode) throws IOException {

        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobGuid));

        Job job = getInternalJob(jobGuid);
        String guid = UUID.randomUUID().toString();

        JobData data = new JobData(guid, JobDataType.GENERATED,useCode,mediaTypeCode);
        JobDataWithByteSink result = new JobDataWithByteSink(data,dataStorageService.put(guid));

        synchronized(this) {
            datas.add(data);
        }

        job.addGeneratedDataGuid(guid);

        return result;
    }

    @Override
    public JobData storeSuppliedData(String useCode, String mediaTypeCode, ByteSource byteSource) throws IOException {
        Preconditions.checkArgument(null!=byteSource, "the byte source must be supplied to provide data");
        String guid = UUID.randomUUID().toString();
        JobData data;
        long len;

        try(InputStream inputStream = byteSource.openStream()) {

            // TODO; constrain this to a sensible size

            len = dataStorageService.put(guid).writeFrom(inputStream);
            data = new JobData(guid, JobDataType.SUPPLIED, useCode, mediaTypeCode);

            synchronized (this) {
                datas.add(data);
            }
        }

        LOGGER.info("did supply {}b job data; {}", len, data);

        return data;
    }

    @Override
    public Optional<JobDataWithByteSource> tryObtainData(String guid) throws IOException {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid), "the guid for the data must be supplied");
        Optional<JobData> jobDataOptional = tryGetData(guid);

        if(jobDataOptional.isPresent()) {
            Optional<? extends ByteSource> byteSourceOptional = dataStorageService.get(guid);

            if(byteSourceOptional.isPresent()) {
                return Optional.of(new JobDataWithByteSource(
                        jobDataOptional.get(), byteSourceOptional.get()));
            }
        }

        return Optional.empty();
    }

    private void tryClearDataStorageService() {
        try {
            dataStorageService.clear();
        }
        catch(Throwable th) {
            LOGGER.error("unable to clear the data storage service", th);
        }
    }

}
