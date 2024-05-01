/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.CollectionUtils;
import org.haiku.haikudepotserver.api2.model.GetJobRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetJobResult;
import org.haiku.haikudepotserver.api2.model.JobStatus;
import org.haiku.haikudepotserver.api2.model.SearchJobsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchJobsResult;
import org.haiku.haikudepotserver.api2.model.SearchJobsResultItem;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Component("jobApiServiceV2")
public class JobApiService extends AbstractApiService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(JobApiService.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final JobService jobService;

    public JobApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public GetJobResult getJob(GetJobRequestEnvelope request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(!Strings.isNullOrEmpty(request.getGuid()));

        final ObjectContext context = serverRuntime.newContext();
        JobSnapshot job = jobService.tryGetJob(request.getGuid())
                .orElseThrow(() -> new ObjectNotFoundException(JobSnapshot.class.getSimpleName(), request.getGuid()));

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

        return new GetJobResult()
                .guid(job.getGuid())
                .jobStatus(JobStatus.valueOf(job.getStatus().name()))
                .jobTypeCode(job.getJobTypeCode())
                .ownerUserNickname(job.getOwnerUserNickname())
                .startTimestamp(Optional.ofNullable(job.getStartTimestamp()).map(Date::getTime).orElse(null))
                .finishTimestamp(Optional.ofNullable(job.getFinishTimestamp()).map(Date::getTime).orElse(null))
                .queuedTimestamp(Optional.ofNullable(job.getQueuedTimestamp()).map(Date::getTime).orElse(null))
                .failTimestamp(Optional.ofNullable(job.getFailTimestamp()).map(Date::getTime).orElse(null))
                .cancelTimestamp(Optional.ofNullable(job.getCancelTimestamp()).map(Date::getTime).orElse(null))
                .progressPercent(job.getProgressPercent())
                .generatedDatas(job.getGeneratedDataGuids().stream()
                        .map(jobService::tryGetData)
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .map(jobData -> new org.haiku.haikudepotserver.api2.model.JobData()
                                .useCode(jobData.getUseCode())
                                .guid(jobData.getGuid())
                                .mediaTypeCode(jobData.getMediaTypeCode())
                                .filename(jobService.deriveDataFilename(jobData.getGuid())))
                        .collect(Collectors.toList()));
    }

    public SearchJobsResult searchJobs(SearchJobsRequestEnvelope request) {
        Preconditions.checkArgument(null != request);

        final ObjectContext context = serverRuntime.newContext();
        User ownerUser = null;

        // authorization

        if (Strings.isNullOrEmpty(request.getOwnerUserNickname())) {
            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    null,
                    Permission.JOBS_VIEW)) {
                throw new AccessDeniedException("attempt to access jobs view");
            }
        }
        else {
            ownerUser = User.tryGetByNickname(context, request.getOwnerUserNickname())
                    .orElseThrow(() -> new ObjectNotFoundException(
                            User.class.getSimpleName(), request.getOwnerUserNickname()));

            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    ownerUser,
                    Permission.USER_VIEWJOBS)) {
                throw new AccessDeniedException("attempt to access jobs view for [" + ownerUser + "]");
            }
        }

        // results

        List<SearchJobsResultItem> items = List.of();
        long total = 0L;

        if (null == request.getStatuses() || CollectionUtils.isNotEmpty(request.getStatuses())) {
            Set<JobSnapshot.Status> statuses = null;

            if (null != request.getStatuses()) {
                statuses = request.getStatuses().stream().map(s -> JobSnapshot.Status.valueOf(s.name())).collect(Collectors.toSet());
            }

            total = jobService.totalJobs(ownerUser, statuses);

            if (total > 0) {
                items = jobService.findJobs(
                                ownerUser,
                                statuses,
                                Optional.ofNullable(request.getOffset()).orElse(0),
                                Optional.ofNullable(request.getLimit()).orElse(Integer.MAX_VALUE))
                        .stream()
                        .map(js -> new SearchJobsResultItem()
                                .guid(js.getGuid())
                                .jobStatus(JobStatus.valueOf(js.getStatus().name()))
                                .jobTypeCode(js.getJobTypeCode())
                                .ownerUserNickname(js.getOwnerUserNickname())
                                .startTimestamp(Optional.ofNullable(js.getStartTimestamp()).map(Date::getTime).orElse(null))
                                .finishTimestamp(Optional.ofNullable(js.getFinishTimestamp()).map(Date::getTime).orElse(null))
                                .queuedTimestamp(Optional.ofNullable(js.getQueuedTimestamp()).map(Date::getTime).orElse(null))
                                .failTimestamp(Optional.ofNullable(js.getFailTimestamp()).map(Date::getTime).orElse(null))
                                .cancelTimestamp(Optional.ofNullable(js.getCancelTimestamp()).map(Date::getTime).orElse(null))
                                .progressPercent(js.getProgressPercent()))
                        .collect(Collectors.toList());
            }

            LOGGER.info("search for jobs found {} results", items.size());
        }

        return new SearchJobsResult()
                .total(total)
                .items(items);
    }

}
