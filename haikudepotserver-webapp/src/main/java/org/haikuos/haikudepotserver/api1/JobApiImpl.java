/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.api1.model.job.*;
import org.haikuos.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobData;
import org.haikuos.haikudepotserver.job.model.JobSnapshot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.Optional;
import java.util.stream.Collectors;

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
        assert null!=request;
        final SearchJobsResult result = new SearchJobsResult();

        final ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);
        Optional<User> ownerUserOptional = Optional.empty();

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

            Set<JobSnapshot.Status> statuses = null;

            if(null!=request.statuses) {
                statuses = request.statuses.stream().map(s -> JobSnapshot.Status.valueOf(s.name())).collect(Collectors.toSet());
            }

            result.total = (long) jobOrchestrationService.totalJobs(ownerUserOptional.orElse(null), statuses);
            result.items = jobOrchestrationService.findJobs(
                            ownerUserOptional.orElse(null),
                            statuses,
                            null == request.offset ? 0 : request.offset,
                            null == request.limit ? Integer.MAX_VALUE : request.limit).stream().map(js -> {
                        SearchJobsResult.Job resultJob = new SearchJobsResult.Job();

                        resultJob.guid = js.getGuid();
                        resultJob.jobStatus = JobStatus.valueOf(js.getStatus().name());
                        resultJob.jobTypeCode = js.getJobTypeCode();
                        resultJob.ownerUserNickname = js.getOwnerUserNickname();
                        resultJob.startTimestamp = null == js.getStartTimestamp() ? null : js.getStartTimestamp().getTime();
                        resultJob.finishTimestamp = null == js.getFinishTimestamp() ? null : js.getFinishTimestamp().getTime();
                        resultJob.queuedTimestamp = null == js.getQueuedTimestamp() ? null : js.getQueuedTimestamp().getTime();
                        resultJob.failTimestamp = null == js.getFailTimestamp() ? null : js.getFailTimestamp().getTime();
                        resultJob.cancelTimestamp = null == js.getCancelTimestamp() ? null : js.getCancelTimestamp().getTime();
                        resultJob.progressPercent = js.getProgressPercent();

                        return resultJob;
                    }).collect(Collectors.toList());

            LOGGER.info("search for jobs found {} results", result.items.size());
        }

        return result;
    }

    @Override
    public GetJobResult getJob(GetJobRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request);
        assert null!=request;
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.guid));

        final ObjectContext context = serverRuntime.getContext();
        User authUser = obtainAuthenticatedUser(context);

        Optional<? extends JobSnapshot> jobOptional = jobOrchestrationService.tryGetJob(request.guid);

        if(!jobOptional.isPresent()) {
            throw new ObjectNotFoundException(JobSnapshot.class.getSimpleName(), request.guid);
        }

        JobSnapshot job = jobOptional.get();

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
        result.jobStatus = JobStatus.valueOf(job.getStatus().name());
        result.jobTypeCode = job.getJobTypeCode();
        result.ownerUserNickname = job.getOwnerUserNickname();
        result.startTimestamp = null == job.getStartTimestamp() ? null : job.getStartTimestamp().getTime();
        result.finishTimestamp = null == job.getFinishTimestamp() ? null : job.getFinishTimestamp().getTime();
        result.queuedTimestamp = null == job.getQueuedTimestamp() ? null : job.getQueuedTimestamp().getTime();
        result.failTimestamp = null == job.getFailTimestamp() ? null : job.getFailTimestamp().getTime();
        result.cancelTimestamp = null == job.getCancelTimestamp() ? null : job.getCancelTimestamp().getTime();
        result.progressPercent = job.getProgressPercent();
        result.generatedDatas = new ArrayList<>();

        // could go functional here, but keeping it simple to keep the exception handling simple.

        for(String guid : job.getGeneratedDataGuids()) {

            Optional<JobData> jobData = jobOrchestrationService.tryGetData(guid);

            if(!jobData.isPresent()) {
                throw new ObjectNotFoundException(JobData.class.getSimpleName(), guid);
            }

            GetJobResult.JobData resultJobData = new GetJobResult.JobData();
            resultJobData.useCode = jobData.get().getUseCode();
            resultJobData.guid = jobData.get().getGuid();
            resultJobData.mediaTypeCode = jobData.get().getMediaTypeCode();
            resultJobData.filename = jobOrchestrationService.deriveDataFilename(guid);

            result.generatedDatas.add(resultJobData);

        }

        return result;
    }

}
