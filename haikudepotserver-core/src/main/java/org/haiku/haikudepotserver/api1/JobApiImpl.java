/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.job.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobData;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Component("jobApiImplV1")
@AutoJsonRpcServiceImpl(additionalPaths = "/api/v1/job") // TODO; legacy path - remove
public class JobApiImpl extends AbstractApiImpl implements JobApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(JobApiImpl.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final JobService jobService;

    public JobApiImpl(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    @Override
    public SearchJobsResult searchJobs(SearchJobsRequest request) {
        Preconditions.checkArgument(null != request);
        final SearchJobsResult result = new SearchJobsResult();

        final ObjectContext context = serverRuntime.newContext();
        User ownerUser = null;

        // authorization

        if (Strings.isNullOrEmpty(request.ownerUserNickname)) {
            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    null,
                    Permission.JOBS_VIEW)) {
                throw new AccessDeniedException("attempt to access jobs view");
            }
        }
        else {
            ownerUser = User.tryGetByNickname(context, request.ownerUserNickname)
                    .orElseThrow(() -> new ObjectNotFoundException(
                            User.class.getSimpleName(), request.ownerUserNickname));

            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    ownerUser,
                    Permission.USER_VIEWJOBS)) {
                throw new AccessDeniedException("attempt to access jobs view for [" + ownerUser + "]");
            }
        }

        // results

        if (null != request.statuses && request.statuses.isEmpty()) {
            result.items = Collections.emptyList();
            result.total = 0L;
        } else {
            Set<JobSnapshot.Status> statuses = null;

            if (null != request.statuses) {
                statuses = request.statuses.stream().map(s -> JobSnapshot.Status.valueOf(s.name())).collect(Collectors.toSet());
            }

            result.total = (long) jobService.totalJobs(ownerUser, statuses);
            result.items = jobService.findJobs(
                            ownerUser,
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
    public GetJobResult getJob(GetJobRequest request) {
        Preconditions.checkArgument(null!=request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.guid));

        final ObjectContext context = serverRuntime.newContext();
        JobSnapshot job = jobService.tryGetJob(request.guid)
                .orElseThrow(() -> new ObjectNotFoundException(JobSnapshot.class.getSimpleName(), request.guid));

        // authorization

        if (Strings.isNullOrEmpty(job.getOwnerUserNickname())) {
            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    null,
                    Permission.JOBS_VIEW)) {
                throw new AccessDeniedException("attempt to access jobs view");
            }
        }
        else {
            User ownerUser = User.tryGetByNickname(context, job.getOwnerUserNickname())
                    .orElseThrow(() -> new ObjectNotFoundException(
                            User.class.getSimpleName(), job.getOwnerUserNickname()));

            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    ownerUser,
                    Permission.USER_VIEWJOBS)) {
                throw new AccessDeniedException("attempt to access jobs view for [" + job + "]");
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

        for (String guid : job.getGeneratedDataGuids()) {
            JobData jobData = jobService.tryGetData(guid)
                    .orElseThrow(() -> new ObjectNotFoundException(JobData.class.getSimpleName(), guid));

            GetJobResult.JobData resultJobData = new GetJobResult.JobData();
            resultJobData.useCode = jobData.getUseCode();
            resultJobData.guid = jobData.getGuid();
            resultJobData.mediaTypeCode = jobData.getMediaTypeCode();
            resultJobData.filename = jobService.deriveDataFilename(guid);

            result.generatedDatas.add(resultJobData);
        }

        return result;
    }

}
