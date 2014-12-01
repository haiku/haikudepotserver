/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.collect.Queues;
import com.google.common.util.concurrent.AbstractService;
import org.haikuos.haikudepotserver.support.job.model.JobRunState;
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

    protected Map<String, JobRunState> jobs;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    private Collection<JobRunState> findJobRunStatesWithStatuses(final EnumSet<JobRunState.Status> statuses) {
        Preconditions.checkArgument(null!=statuses, "the status must be supplied to filter the job run states");
        assert statuses != null;

        if(statuses.isEmpty()) {
            return Collections.emptySet();
        }

        return ImmutableList.copyOf(
                Iterables.filter(
                        jobs.values(),
                        new Predicate<JobRunState>() {
                            @Override
                            public boolean apply(JobRunState input) {
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
        JobRunState jobRunState = new JobRunState();
        jobRunState.setJobSpecification(specification);

        LOGGER.debug("{}; will submit job", specification.toString());
        jobs.put(jobRunState.getGuid(), jobRunState);
        setJobRunQueuedTimestamp(specification.getGuid());
        executor.submit(new JobRunnable(jobRunState.getJobSpecification()));
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
                if(!tryGetJobRunStateWithEquivalentSpecification(
                        specification,
                        findJobRunStatesWithStatuses(EnumSet.of(JobRunState.Status.QUEUED))).isPresent()) {
                    return Optional.of(submit(specification));
                }
                break;

            case QUEUEDANDSTARTED:
                if(!tryGetJobRunStateWithEquivalentSpecification(
                        specification,
                        findJobRunStatesWithStatuses(
                                EnumSet.of(JobRunState.Status.QUEUED,JobRunState.Status.STARTED))
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

            setJobRunFailTimestamp(specification.getGuid());
        }

        try {
            setJobRunStartTimestamp(specification.getGuid());
            //noinspection unchecked
            jobRunnerOptional.get().run(specification);
            setJobRunFinishTimestamp(specification.getGuid());
        }
        catch(Throwable th) {
            LOGGER.error(specification.getGuid() + "; failure to run the job", th);
            setJobRunFailTimestamp(specification.getGuid());
        }
    }

    // ------------------------------
    // PURGE

    private long deriveTimeToLive(JobRunState runState) {
        Long timeToLive = runState.getTimeToLive();

        if(null==timeToLive) {
            return TTL_DEFAULT;
        }

        return timeToLive;
    }

    @Override
    public synchronized void clearExpiredJobs() {
        long nowMillis = System.currentTimeMillis();
        List<JobRunState> jobsCopy;

        synchronized (this) {
            jobsCopy = ImmutableList.copyOf(jobs.values());
        }

        for(JobRunState runState : jobsCopy) {

            Long ttl = deriveTimeToLive(runState);
            Date quiesenceTimestamp = null;

            switch(runState.getStatus()) {

                case CANCELLED:
                    quiesenceTimestamp = runState.getCancelTimestamp();
                    break;

                case FINISHED:
                    quiesenceTimestamp = runState.getFinishTimestamp();
                    break;

                case FAILED:
                    quiesenceTimestamp = runState.getFailTimestamp();
                    break;

            }

            if(null!=quiesenceTimestamp) {
                if(nowMillis - quiesenceTimestamp.getTime() > ttl) {

                    synchronized(this) {
                        jobs.remove(runState.getGuid());
                    }

                    LOGGER.info(
                            "{} purged expired job for ttl; {}ms",
                            runState.getJobSpecification().toString(),
                            ttl);
                }
            }

        }
    }

    // ------------------------------
    // RUN STATE MANIPULATION

    /**
     * <p>Parameters from this are concurrency-safe from caller.</p>
     */

    private static Optional<JobRunState> tryGetJobRunStateWithEquivalentSpecification(
            JobSpecification other,
            Collection<JobRunState> jobRunStates) {
        Preconditions.checkArgument(null!=other, "need to provide the other job specification");

        for(JobRunState jobRunState : ImmutableList.copyOf(jobRunStates)) {
            assert other != null;
            if(other.isEquivalent(jobRunState.getJobSpecification())) {
                return Optional.of(jobRunState);
            }
        }

        return Optional.absent();
    }

    @Override
    public synchronized Optional<JobRunState> tryGetJobRunState(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        JobRunState jobRunState = jobs.get(guid);

        if(null!=jobRunState) {
            return Optional.of(new JobRunState(jobRunState));
        }

        return Optional.absent();
    }

    /**
     * <p>Note that this method does not return a copy of the
     * {@link org.haikuos.haikudepotserver.support.job.model.JobRunState}; it will return
     * the working copy.</p>
     */

    private JobRunState getJobRunState(String guid) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(guid));
        JobRunState jobRunState = jobs.get(guid);

        if(null==jobRunState) {
            throw new IllegalStateException("no job run state exists for; " + guid);
        }

        return jobRunState;
    }

    public synchronized void setJobRunStartTimestamp(String guid) {
        JobRunState state = getJobRunState(guid);

        if(state.getStatus() != JobRunState.Status.QUEUED) {
            throw new IllegalStateException("it is not possible to start a job from status; " + state.getStatus());
        }

        state.setStartTimestamp();
        LOGGER.info("{}; start", state.getJobSpecification().toString());
    }

    public synchronized void setJobRunFinishTimestamp(String guid) {
        JobRunState state = getJobRunState(guid);

        if(state.getStatus() != JobRunState.Status.STARTED) {
            throw new IllegalStateException("it is not possible to finish a job from status; " + state.getStatus());
        }

        state.setFinishTimestamp();
        LOGGER.info("{}; finish", state.getJobSpecification().toString());
    }

    public synchronized void setJobRunQueuedTimestamp(String guid) {
        JobRunState state = getJobRunState(guid);

        if(state.getStatus() != JobRunState.Status.INDETERMINATE) {
            throw new IllegalStateException("it is not possible to queue a job from status; " + state.getStatus());
        }

        state.setQueuedTimestamp();
        LOGGER.info("{}; queued", state.getJobSpecification().toString());
    }

    @Override
    public synchronized void setJobRunFailTimestamp(String guid) {
        JobRunState state = getJobRunState(guid);

        if(state.getStatus() != JobRunState.Status.STARTED) {
            throw new IllegalStateException("it is not possible to fail a job from status; " + state.getStatus());
        }

        state.setFailTimestamp();
        LOGGER.info("{}; fail", state.getJobSpecification().toString());
    }

    @Override
    public synchronized void setJobRunCancelTimestamp(String guid) {
        JobRunState state = getJobRunState(guid);

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
    public synchronized void setJobRunProgressPercent(String guid, Integer progressPercent) {
        Preconditions.checkArgument(null==progressPercent || (progressPercent >= 0 && progressPercent <= 100), "bad progress percent value");

        JobRunState state = getJobRunState(guid);

        if(state.getStatus() != JobRunState.Status.STARTED) {
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
