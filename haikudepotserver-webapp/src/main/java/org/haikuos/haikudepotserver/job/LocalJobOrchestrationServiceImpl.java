/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import com.google.common.util.concurrent.AbstractService;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.job.model.*;
import org.haikuos.haikudepotserver.support.DateTimeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>This concrete implementation of the {@link org.haikuos.haikudepotserver.job.JobOrchestrationService}
 * is able to run jobs locally in the virtual machine; it does not distribute or coordinate the run-state of the
 * jobs across virtual machines etc...</p>
 */

public class LocalJobOrchestrationServiceImpl
        extends AbstractService
        implements ApplicationContextAware, JobOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(JobOrchestrationService.class);

    public final static int SIZE_QUEUE = 10;

    public final static long TTL_DEFAULT = 2 * 60 * 60 * 1000; // 2h

    private JobDataStorageService dataStorageService;

    private ThreadPoolExecutor executor = null;

    private ArrayBlockingQueue<Runnable> runnables = Queues.newArrayBlockingQueue(SIZE_QUEUE);

    /**
     * <p>Contains a mapping from the job type code to a suitable runner for that type code.</p>
     */

    private Map<String,JobRunner> jobRunners;

    /**
     * <p>Contains a mapping from the GUID to the job.</p>
     */

    private Map<String, Job> jobs;

    /**
     * <p>Job data that the system knows about.</p>
     */

    private Set<JobData> datas = Sets.newHashSet();

    private ApplicationContext applicationContext;

    public void setJobDataStorageService(JobDataStorageService dataStorageService) {
        this.dataStorageService = dataStorageService;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private synchronized Collection<Job> findJobsWithStatuses(final EnumSet<JobSnapshot.Status> statuses) {
        Preconditions.checkArgument(null!=statuses, "the status must be supplied to filter the job run states");
        assert statuses != null;

        if(statuses.isEmpty()) {
            return Collections.emptySet();
        }

        return ImmutableList.copyOf(
                Iterables.filter(
                        jobs.values(),
                        new Predicate<Job>() {
                            @Override
                            public boolean apply(Job input) {
                                return statuses.contains(input.getStatus());
                            }
                        }
                )
        );
    }

    // ------------------------------
    // RUN JOBS

    private Optional<JobRunner> getJobRunner(String jobTypeCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobTypeCode));
        Preconditions.checkState(null!=jobRunners,"the job runners must be configured - was this started up properly?");
        return Optional.fromNullable(jobRunners.get(jobTypeCode));
    }

    private Job submit(JobSpecification specification) {
        Job job = new Job();
        job.setJobSpecification(specification);

        LOGGER.debug("{}; will submit job", specification.toString());
        jobs.put(job.getGuid(), job);
        setJobRunQueuedTimestamp(specification.getGuid());
        executor.submit(new JobRunnable(job.getJobSpecification()));
        LOGGER.debug("{}; did submit job", specification.toString());

        return job;
    }

    @Override
    public synchronized Optional<String> submit(
            JobSpecification specification,
            CoalesceMode coalesceMode) {

        Preconditions.checkState(null!=executor, "the executor has not been configured; was this service started correctly?");
        Preconditions.checkArgument(null!=specification);

        assert specification != null;
        Optional<JobRunner> jobRunnerOptional = getJobRunner(specification.getJobTypeCode());

        if(!jobRunnerOptional.isPresent()) {
            throw new IllegalStateException("unable to run a job runner for the job type code; " + specification.getJobTypeCode());
        }

        for(String guid : specification.getSuppliedDataGuids()) {
            if(!tryGetData(guid).isPresent()) {
                throw new IllegalStateException("unable to run a job specification because the specified data " + guid + " was not able to be found");
            }
        }

        if(null==specification.getGuid()) {
            specification.setGuid(UUID.randomUUID().toString());
        }

        switch(coalesceMode) {

            case NONE:
                return Optional.of(submit(specification).getGuid());

            case QUEUED:
                if(!tryGetJobWithEquivalentSpecification(
                        specification,
                        findJobsWithStatuses(EnumSet.of(JobSnapshot.Status.QUEUED))).isPresent()) {
                    return Optional.of(submit(specification).getGuid());
                }
                break;

            case QUEUEDANDSTARTED:
                if(!tryGetJobWithEquivalentSpecification(
                        specification,
                        findJobsWithStatuses(
                                EnumSet.of(JobSnapshot.Status.QUEUED, JobSnapshot.Status.STARTED))
                ).isPresent()) {
                    return Optional.of(submit(specification).getGuid());
                }
                break;

        }

        return Optional.absent();
    }

    private void runInternal(JobSpecification specification) {
        Preconditions.checkArgument(null!=specification, "the job specification must be supplied to run the job");

        assert specification != null;
        Optional<JobRunner> jobRunnerOptional = getJobRunner(specification.getJobTypeCode());

        if(!jobRunnerOptional.isPresent()) {
            LOGGER.error(
                    "{}; there is no job runner available for job type code '{}'; - failing",
                    specification.toString(),
                    specification.getJobTypeCode());

            setJobFailTimestamp(specification.getGuid());
        }

        try {
            setJobStartTimestamp(specification.getGuid());
            //noinspection unchecked
            jobRunnerOptional.get().run(this, specification);
            setJobFinishTimestamp(specification.getGuid());
        }
        catch(Throwable th) {
            LOGGER.error(specification.getGuid() + "; failure to run the job", th);
            setJobFailTimestamp(specification.getGuid());
        }
    }

    // ------------------------------
    // PURGE

    private long deriveTimeToLive(JobSnapshot job) {
        Long timeToLive = job.getTimeToLive();

        if(null==timeToLive) {
            return TTL_DEFAULT;
        }

        return timeToLive;
    }

    private synchronized Optional<Job> tryFindJob(final JobData data) {
        Preconditions.checkArgument(null!=data);
        assert null!=data;

        return Iterables.tryFind(
                jobs.values(),
                new Predicate<Job>() {
                    @Override
                    public boolean apply(Job input) {
                        return input.getDataGuids().contains(data.getGuid());
                    }
                }
        );
    }

    /**
     * <p>Job data may be stored ready to be attached to a job.  If the attachment never
     * happens then the job data may hang around indefinitely.  This clearing process will
     * cull those job datas that have not been used before time reasonable timeout.</p>
     */

    private synchronized void clearExpiredDatas() {
        for(JobData data : ImmutableList.copyOf(datas)) {
            if(System.currentTimeMillis() - data.getCreateTimestamp().getTime() > TTL_DEFAULT) {
                if(!tryFindJob(data).isPresent()) {
                    if(dataStorageService.remove(data.getGuid())) {
                        LOGGER.info("did delete the expired unassociated job data; {}", data);
                        datas.remove(data);
                    }
                    else {
                        LOGGER.error("was not able to delete the expired unassociated job data; {} - data will remain in situ", data);
                    }
                }
            }
        }
    }

    /**
     * <p>Removes the job and any data associated with the job.</p>
     */

    private synchronized void removeJob(Job job) {
        Preconditions.checkArgument(null!=job, "the job must be supplied to remove a job");
        assert null!=job;

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
    public synchronized void clearExpiredJobs() {
        long nowMillis = System.currentTimeMillis();

        synchronized (this) {

            for (Job job : ImmutableList.copyOf(jobs.values())) {

                Long ttl = deriveTimeToLive(job);
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

                        removeJob(job);

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
    // RUN STATE MANIPULATION

    private synchronized List<Job> filteredJobs(
            final User user,
            final Set<JobSnapshot.Status> statuses) {

        clearExpiredJobs();

        if(null!=statuses && statuses.isEmpty()) {
            return Collections.emptyList();
        }

        return Lists.newArrayList(
                Iterables.transform(
                        Iterables.filter(
                                jobs.values(),
                                new Predicate<Job>() {
                                    @Override
                                    public boolean apply(Job input) {
                                        return
                                                (null == user || user.getNickname().equals(input.getOwnerUserNickname()))
                                                        && (null == statuses || statuses.contains(input.getStatus()));
                                    }
                                }
                        ),
                        new Function<Job, Job>() {
                            @Override
                            public Job apply(Job input) {
                                return new Job(input);
                            }
                        }
                )
        );
    }

    @Override
    public List<? extends JobSnapshot> findJobs(
            final User user,
            final Set<JobSnapshot.Status> statuses,
            int offset,
            int limit) {

        Preconditions.checkState(offset >= 0, "illegal offset value");
        Preconditions.checkState(limit >= 1, "illegal limit value");

        if(null!=statuses && statuses.isEmpty()) {
            return Collections.emptyList();
        }

        List<Job> result = filteredJobs(user, statuses);

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
        return filteredJobs(user, statuses).size();
    }

    /**
     * <p>Parameters from this are concurrency-safe from caller.</p>
     */

    private static Optional<JobSnapshot> tryGetJobWithEquivalentSpecification(
            JobSpecification other,
            Collection<Job> jobs) {
        Preconditions.checkArgument(null!=other, "need to provide the other job specification");

        for(JobSnapshot job : ImmutableList.copyOf(jobs)) {
            assert other != null;
            if(other.isEquivalent(job.getJobSpecification())) {
                return Optional.of(job);
            }
        }

        return Optional.absent();
    }

    @Override
    public synchronized Optional<? extends JobSnapshot> tryGetJob(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        JobSnapshot job = jobs.get(guid);

        if(null!=job) {
            return Optional.of(new Job(job));
        }

        return Optional.absent();
    }

    /**
     * <p>Note that this method does not return a copy of the
     * {@link org.haikuos.haikudepotserver.job.model.JobSnapshot}; it will return
     * the working copy.</p>
     */

    private Job getJob(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        Job job = jobs.get(guid);

        if(null==job) {
            throw new IllegalStateException("no job run state exists for; " + guid);
        }

        return job;
    }

    public synchronized void setJobStartTimestamp(String guid) {
        Job job = getJob(guid);

        if(job.getStatus() != JobSnapshot.Status.QUEUED) {
            throw new IllegalStateException("it is not possible to start a job from status; " + job.getStatus());
        }

        job.setStartTimestamp();
        LOGGER.info("{}; start", job.getJobSpecification().toString());
    }

    public synchronized void setJobFinishTimestamp(String guid) {
        Job job = getJob(guid);

        if(job.getStatus() != JobSnapshot.Status.STARTED) {
            throw new IllegalStateException("it is not possible to finish a job from status; " + job.getStatus());
        }

        job.setFinishTimestamp();
        LOGGER.info("{}; finish", job.getJobSpecification().toString());
    }

    public synchronized void setJobRunQueuedTimestamp(String guid) {
        Job job = getJob(guid);

        if(job.getStatus() != JobSnapshot.Status.INDETERMINATE) {
            throw new IllegalStateException("it is not possible to queue a job from status; " + job.getStatus());
        }

        job.setQueuedTimestamp();
        LOGGER.info("{}; queued", job.getJobSpecification().toString());
    }

    @Override
    public synchronized void setJobFailTimestamp(String guid) {
        Job job = getJob(guid);

        if(job.getStatus() != JobSnapshot.Status.STARTED) {
            throw new IllegalStateException("it is not possible to fail a job from status; " + job.getStatus());
        }

        job.setFailTimestamp();
        LOGGER.info("{}; fail", job.getJobSpecification().toString());
    }

    @Override
    public synchronized void setJobCancelTimestamp(String guid) {
        Job job = getJob(guid);

        switch (job.getStatus()) {
            case QUEUED:
            case STARTED:
                job.setCancelTimestamp();
                LOGGER.info("{}; cancelled", job.getJobSpecification().toString());
                break;

            default:
                throw new IllegalStateException("it is not possible to cancel a job from status; " + job.getStatus());
        }
    }

    @Override
    public synchronized void setJobProgressPercent(String guid, Integer progressPercent) {
        Preconditions.checkArgument(null==progressPercent || (progressPercent >= 0 && progressPercent <= 100), "bad progress percent value");
        Job job = getJob(guid);

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
    }

    // ------------------------------
    // SERVICE START / STOP LIFECYCLE

    @Override
    public void doStart() {
        try {
            Preconditions.checkState(null == executor);

            LOGGER.info("will start service");

            jobs = Maps.newHashMap();

            jobRunners = Maps.newHashMap();

            for(JobRunner jobRunner : applicationContext.getBeansOfType(JobRunner.class).values()) {
                jobRunners.put(jobRunner.getJobTypeCode(), jobRunner);
                LOGGER.info(
                        "registered job runner; {} ({})",
                        jobRunner.getJobTypeCode(),
                        jobRunner.getClass().getSimpleName());
            }

            executor = new ThreadPoolExecutor(
                    0, // core pool size
                    1, // max pool size
                    1l, // time to shutdown threads
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

            jobRunners = null;
            jobs = null;

            LOGGER.info("did stop service");
        }
        catch(Throwable th) {
            notifyFailed(th);
        }
    }

    private void tryClearDataStorageService() {
        try {
            dataStorageService.clear();
        }
        catch(Throwable th) {
            LOGGER.error("unable to clear the data storage service", th);
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

    /**
     * <p>Returns true if the service is actively working on a job or it has a job submitted which has not yet
     * been dequeued and run.</p>
     */

    public boolean isProcessingSubmittedJobs() {
        return
                null!=executor
                        && (executor.getActiveCount() > 0 || !executor.getQueue().isEmpty());
    }

    /**
     * <p>This wrapper takes one of the queued jobs and runs it.</p>
     */

    public class JobRunnable implements Runnable {

        private JobSpecification specification;

        public JobRunnable(JobSpecification specification) {
            this.specification = specification;
        }

        @Override
        public void run() {

            String threadNamePrior = Thread.currentThread().getName();

            try {
                String g = specification.getGuid();

                if(g.length() > 4) {
                    g = g.substring(0,4) + "..";
                }

                Thread.currentThread().setName("job-run-"+g);

                runInternal(specification);
            }
            finally {
                Thread.currentThread().setName(threadNamePrior);
            }

        }

    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobDataGuid));
        Optional<JobData> jobDataOptional = tryGetData(jobDataGuid);

        if(jobDataOptional.isPresent()) {
            return tryFindJob(jobDataOptional.get());
        }

        return Optional.absent();
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
            }
        }

        return String.format(
                "hds_%s_%s_%s.%s",
                descriptor,
                DateTimeHelper.create14DigitDateTimeFormat().print(System.currentTimeMillis()),
                jobDataGuid.substring(0,4),
                extension);
    }

    @Override
    public synchronized Optional<JobData> tryGetData(final String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid), "the guid must be supplied");
        return Iterables.tryFind(
                datas,
                new Predicate<JobData>() {
                    @Override
                    public boolean apply(JobData input) {
                        return input.getGuid().equals(guid);
                    }
                }
        );
    }

    @Override
    public JobDataWithByteSink storeGeneratedData(
            String jobGuid,
            String useCode,
            String mediaTypeCode) throws IOException {

        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobGuid));

        Job job = getJob(jobGuid);
        String guid = UUID.randomUUID().toString();

        JobData data = new JobData(guid,JobDataType.GENERATED,useCode,mediaTypeCode);
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
        assert null!=byteSource;
        String guid = UUID.randomUUID().toString();
        JobData data;
        long len;

        try(InputStream inputStream = byteSource.openStream()) {
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

        return Optional.absent();
    }

}
