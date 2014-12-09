/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Function;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.job.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.support.job.model.Job;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Set;

@Component
public class JobApiImpl extends AbstractApiImpl implements JobApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(JobApiImpl.class);

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Resource
    private ServerRuntime serverRuntime;

    @Override
    public SearchJobsResult searchJobs(SearchJobsRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request);
        final SearchJobsResult result = new SearchJobsResult();

        final ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);
        Optional<User> ownerUserOptional = Optional.absent();

        // authorization

        if(Strings.isNullOrEmpty(request.ownerUserNickname)) {
            if (!authorizationService.check(context, authUser, null, Permission.JOBS_VIEW)) {
                LOGGER.warn("attempt to access jobs view");
                throw new AuthorizationFailureException();
            }
        }
        else {
            ownerUserOptional = User.getByNickname(context, request.ownerUserNickname);

            if(!ownerUserOptional.isPresent()) {
                throw new ObjectNotFoundException(User.class.getSimpleName(), request.ownerUserNickname);
            }

            if (!authorizationService.check(context, authUser, ownerUserOptional.get(), Permission.USER_VIEWJOBS)) {
                LOGGER.warn("attempt to access jobs view for; {}", ownerUserOptional.get().toString());
                throw new AuthorizationFailureException();
            }
        }

        // results

        if(null!=request.statuses && request.statuses.isEmpty()) {
            result.items = Collections.emptyList();
            result.total = 0L;
        }
        else {
            final JobStatusToApiJobStatus toApiStatusFn = new JobStatusToApiJobStatus();
            final ApiJobStatusToJobStatus toStatusFn = new ApiJobStatusToJobStatus();

            Set<Job.Status> statuses = null;

            if(null!=request.statuses) {
                statuses = ImmutableSet.copyOf(Iterables.transform(request.statuses,toStatusFn));
            }

            result.total = (long) jobOrchestrationService.totalJobs(ownerUserOptional.orNull(), statuses);
            result.items = Lists.transform(
                    jobOrchestrationService.findJobs(
                            ownerUserOptional.orNull(),
                            statuses,
                            null == request.offset ? 0 : request.offset,
                            null == request.limit ? Integer.MAX_VALUE : request.limit),
                    new Function<Job, SearchJobsResult.Job>() {
                        @Override
                        public SearchJobsResult.Job apply(Job input) {
                            SearchJobsResult.Job resultJob = new SearchJobsResult.Job();

                            resultJob.guid = input.getGuid();
                            resultJob.jobStatus = toApiStatusFn.apply(input.getStatus());
                            resultJob.jobTypeCode = input.getJobTypeCode();
                            resultJob.ownerUserNickname = input.getOwnerUserNickname();
                            resultJob.startTimestamp = null == input.getStartTimestamp() ? null : input.getStartTimestamp().getTime();
                            resultJob.finishTimestamp = null == input.getFinishTimestamp() ? null : input.getFinishTimestamp().getTime();
                            resultJob.queuedTimestamp = null == input.getQueuedTimestamp() ? null : input.getQueuedTimestamp().getTime();
                            resultJob.failTimestamp = null == input.getFailTimestamp() ? null : input.getFailTimestamp().getTime();
                            resultJob.cancelTimestamp = null == input.getCancelTimestamp() ? null : input.getCancelTimestamp().getTime();
                            resultJob.progressPercent = input.getProgressPercent();

                            return resultJob;
                        }
                    }
            );

            LOGGER.info("search for jobs found {} results", result.items.size());
        }

        return result;
    }

    @Override
    public GetJobResult getJob(GetJobRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.guid));

        final ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);

        Optional<Job> jobOptional = jobOrchestrationService.tryGetJob(request.guid);

        if(!jobOptional.isPresent()) {
            throw new ObjectNotFoundException(Job.class.getSimpleName(), request.guid);
        }

        Job job = jobOptional.get();

        // authorization

        if(Strings.isNullOrEmpty(job.getOwnerUserNickname())) {
            if (!authorizationService.check(context, authUser, null, Permission.JOBS_VIEW)) {
                LOGGER.warn("attempt to access jobs view");
                throw new AuthorizationFailureException();
            }
        }
        else {
            Optional<User> ownerUserOptional = User.getByNickname(context, job.getOwnerUserNickname());

            if(!ownerUserOptional.isPresent()) {
                throw new ObjectNotFoundException(User.class.getSimpleName(), job.getOwnerUserNickname());
            }

            if (!authorizationService.check(context, authUser, ownerUserOptional.get(), Permission.USER_VIEWJOBS)) {
                LOGGER.warn("attempt to access jobs view for; {}", job.toString());
                throw new AuthorizationFailureException();
            }
        }

        // now output the result.

        GetJobResult result = new GetJobResult();

        result.guid = job.getGuid();
        result.jobStatus = new JobStatusToApiJobStatus().apply(job.getStatus());
        result.jobTypeCode = job.getJobTypeCode();
        result.ownerUserNickname = job.getOwnerUserNickname();
        result.startTimestamp = null == job.getStartTimestamp() ? null : job.getStartTimestamp().getTime();
        result.finishTimestamp = null == job.getFinishTimestamp() ? null : job.getFinishTimestamp().getTime();
        result.queuedTimestamp = null == job.getQueuedTimestamp() ? null : job.getQueuedTimestamp().getTime();
        result.failTimestamp = null == job.getFailTimestamp() ? null : job.getFailTimestamp().getTime();
        result.cancelTimestamp = null == job.getCancelTimestamp() ? null : job.getCancelTimestamp().getTime();
        result.progressPercent = job.getProgressPercent();

        return result;
    }

    private static class ApiJobStatusToJobStatus implements Function<JobStatus, Job.Status> {

        @Override
        public Job.Status apply(JobStatus input) {
            return Job.Status.valueOf(input.name());
        }

    }

    private static class JobStatusToApiJobStatus implements Function<Job.Status, JobStatus> {

        @Override
        public JobStatus apply(Job.Status input) {
            return JobStatus.valueOf(input.name());
        }

    }

}
