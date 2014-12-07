/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job;

import com.google.common.base.*;
import com.google.common.collect.*;
import com.google.common.util.concurrent.AbstractService;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.support.job.model.Job;
import org.haikuos.haikudepotserver.support.job.model.JobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;

import java.util.*;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * <p>This concrete implementation of the {@link org.haikuos.haikudepotserver.support.job.JobOrchestrationService}
 * is able to run jobs locally in the virtual machine; it does not distribute or coordinate the run-state of the
 * jobs across virtual machines etc...</p>
 */

public class LocalJobOrchestrationService
        extends AbstractService
        implements ApplicationContextAware, JobOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(JobOrchestrationService.class);

    public final static int SIZE_QUEUE = 10;

    public final static long TTL_DEFAULT = 2 * 60 * 60 * 1000; // 2h

    protected ThreadPoolExecutor executor = null;

    protected ArrayBlockingQueue<Runnable> runnables = Queues.newArrayBlockingQueue(SIZE_QUEUE);

    protected Map<String,JobRunner> jobRunners;

    protected Map<String, Job> jobs;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private Collection<Job> findJobsWithStatuses(final EnumSet<Job.Status> statuses) {
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

    private String submit(JobSpecification specification) {
        Job job = new Job();
        job.setJobSpecification(specification);

        LOGGER.debug("{}; will submit job", specification.toString());
        jobs.put(job.getGuid(), job);
        setJobRunQueuedTimestamp(specification.getGuid());
        executor.submit(new JobRunnable(job.getJobSpecification()));
        LOGGER.debug("{}; did submit job", specification.toString());

        return specification.getGuid();
    }

    @Override
    public synchronized Optional<String> submit(JobSpecification specification, CoalesceMode coalesceMode) {

        Preconditions.checkState(null!=executor, "the executor has not been configured; was this service started correctly?");
        Preconditions.checkArgument(null!=specification);

        assert specification != null;
        Optional<JobRunner> jobRunnerOptional = getJobRunner(specification.getJobTypeCode());

        if(!jobRunnerOptional.isPresent()) {
            throw new IllegalStateException("unable to run a job runner for the job type code; " + specification.getJobTypeCode());
        }

        if(null==specification.getGuid()) {
            specification.setGuid(UUID.randomUUID().toString());
        }

        switch(coalesceMode) {

            case NONE:
                return Optional.of(submit(specification));

            case QUEUED:
                if(!tryGetJobWithEquivalentSpecification(
                        specification,
                        findJobsWithStatuses(EnumSet.of(Job.Status.QUEUED))).isPresent()) {
                    return Optional.of(submit(specification));
                }
                break;

            case QUEUEDANDSTARTED:
                if(!tryGetJobWithEquivalentSpecification(
                        specification,
                        findJobsWithStatuses(
                                EnumSet.of(Job.Status.QUEUED, Job.Status.STARTED))
                ).isPresent()) {
                    return Optional.of(submit(specification));
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
            jobRunnerOptional.get().run(specification);
            setJobFinishTimestamp(specification.getGuid());
        }
        catch(Throwable th) {
            LOGGER.error(specification.getGuid() + "; failure to run the job", th);
            setJobFailTimestamp(specification.getGuid());
        }
    }

    // ------------------------------
    // PURGE

    private long deriveTimeToLive(Job job) {
        Long timeToLive = job.getTimeToLive();

        if(null==timeToLive) {
            return TTL_DEFAULT;
        }

        return timeToLive;
    }

    @Override
    public synchronized void clearExpiredJobs() {
        long nowMillis = System.currentTimeMillis();
        List<Job> jobsCopy;

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

                        synchronized (this) {
                            jobs.remove(job.getGuid());
                        }

                        LOGGER.info(
                                "{} purged expired job for ttl; {}ms",
                                job.getJobSpecification().toString(),
                                ttl);
                    }
                }

            }
        }
    }

    // ------------------------------
    // RUN STATE MANIPULATION

    private synchronized List<Job> filteredJobs(
            final User user,
            final Set<Job.Status> statuses) {

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
    public List<Job> findJobs(
            final User user,
            final Set<Job.Status> statuses,
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
    public int totalJobs(User user, Set<Job.Status> statuses) {
        return filteredJobs(user, statuses).size();
    }

    /**
     * <p>Parameters from this are concurrency-safe from caller.</p>
     */

    private static Optional<Job> tryGetJobWithEquivalentSpecification(
            JobSpecification other,
            Collection<Job> jobs) {
        Preconditions.checkArgument(null!=other, "need to provide the other job specification");

        for(Job job : ImmutableList.copyOf(jobs)) {
            assert other != null;
            if(other.isEquivalent(job.getJobSpecification())) {
                return Optional.of(job);
            }
        }

        return Optional.absent();
    }

    @Override
    public synchronized Optional<Job> tryGetJob(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        Job job = jobs.get(guid);

        if(null!=job) {
            return Optional.of(new Job(job));
        }

        return Optional.absent();
    }

    /**
     * <p>Note that this method does not return a copy of the
     * {@link org.haikuos.haikudepotserver.support.job.model.Job}; it will return
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
        Job state = getJob(guid);

        if(state.getStatus() != Job.Status.QUEUED) {
            throw new IllegalStateException("it is not possible to start a job from status; " + state.getStatus());
        }

        state.setStartTimestamp();
        LOGGER.info("{}; start", state.getJobSpecification().toString());
    }

    public synchronized void setJobFinishTimestamp(String guid) {
        Job state = getJob(guid);

        if(state.getStatus() != Job.Status.STARTED) {
            throw new IllegalStateException("it is not possible to finish a job from status; " + state.getStatus());
        }

        state.setFinishTimestamp();
        LOGGER.info("{}; finish", state.getJobSpecification().toString());
    }

    public synchronized void setJobRunQueuedTimestamp(String guid) {
        Job state = getJob(guid);

        if(state.getStatus() != Job.Status.INDETERMINATE) {
            throw new IllegalStateException("it is not possible to queue a job from status; " + state.getStatus());
        }

        state.setQueuedTimestamp();
        LOGGER.info("{}; queued", state.getJobSpecification().toString());
    }

    @Override
    public synchronized void setJobFailTimestamp(String guid) {
        Job state = getJob(guid);

        if(state.getStatus() != Job.Status.STARTED) {
            throw new IllegalStateException("it is not possible to fail a job from status; " + state.getStatus());
        }

        state.setFailTimestamp();
        LOGGER.info("{}; fail", state.getJobSpecification().toString());
    }

    @Override
    public synchronized void setJobCancelTimestamp(String guid) {
        Job state = getJob(guid);

        switch (state.getStatus()) {
            case QUEUED:
            case STARTED:
                state.setCancelTimestamp();
                LOGGER.info("{}; cancelled", state.getJobSpecification().toString());
                break;

            default:
                throw new IllegalStateException("it is not possible to cancel a job from status; " + state.getStatus());
        }
    }

    @Override
    public synchronized void setJobProgressPercent(String guid, Integer progressPercent) {
        Preconditions.checkArgument(null==progressPercent || (progressPercent >= 0 && progressPercent <= 100), "bad progress percent value");

        Job state = getJob(guid);

        if(state.getStatus() != Job.Status.STARTED) {
            throw new IllegalStateException("it is not possible to set the progress percent for a job from status; " + state.getStatus());
        }

        Integer priorProgressPercent = state.getProgressPercent();

        if(null!=progressPercent) {
            if(null==priorProgressPercent || priorProgressPercent.intValue() != progressPercent.intValue()) {
                LOGGER.info("{}; progress {}%", state.getJobSpecification().toString(), progressPercent);
            }
        }

        state.setProgressPercent(progressPercent);
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

    public void startAsyncAndAwaitRunning() {
        startAsync();
        awaitRunning();
    }

    public void stopAsyncAndAwaitTerminated() {
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

}
