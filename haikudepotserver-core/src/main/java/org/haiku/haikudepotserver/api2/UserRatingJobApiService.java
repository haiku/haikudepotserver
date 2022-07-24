/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api2.model.QueueUserRatingSpreadsheetJobRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.QueueUserRatingSpreadsheetJobResult;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.userrating.model.UserRatingSpreadsheetJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;

@Component("userRatingJobApiServiceV2")
public class UserRatingJobApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingJobApiService.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final JobService jobService;

    public UserRatingJobApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public QueueUserRatingSpreadsheetJobResult queueUserRatingSpreadsheetJob(QueueUserRatingSpreadsheetJobRequestEnvelope request) {
        Preconditions.checkArgument(null != request);
        Preconditions.checkArgument(Strings.isNullOrEmpty(request.getPkgName()) || Strings.isNullOrEmpty(request.getUserNickname()),"the user nickname or pkg name can be supplied, but not both");

        final ObjectContext context = serverRuntime.newContext();

        User user = obtainAuthenticatedUser(context);
        UserRatingSpreadsheetJobSpecification spec = new UserRatingSpreadsheetJobSpecification();

        if(!Strings.isNullOrEmpty(request.getRepositoryCode())) {
            spec.setRepositoryCode(getRepository(context, request.getRepositoryCode()).getCode());
        }

        if(!Strings.isNullOrEmpty(request.getUserNickname())) {
            Optional<User> requestUserOptional = User.tryGetByNickname(context, request.getUserNickname());

            if(requestUserOptional.isEmpty()) {
                throw new AccessDeniedException("attempt to produce user rating report for user ["
                        + request.getUserNickname() + "], but that user does not exist -- not allowed");
            }

            if(!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    requestUserOptional.get(),
                    Permission.BULK_USERRATINGSPREADSHEETREPORT_USER)) {
                throw new AccessDeniedException(
                        "attempt to access a user rating report for user ["
                                + request.getUserNickname() + "], but this was disallowed");
            }

            spec.setUserNickname(request.getUserNickname());
        }
        else {
            if (!Strings.isNullOrEmpty(request.getPkgName())) {
                Optional<Pkg> requestPkgOptional = Pkg.tryGetByName(context, request.getPkgName());

                if (requestPkgOptional.isEmpty()) {
                    throw new AccessDeniedException(
                            "attempt to produce user rating report for pkg ["
                                    + request.getPkgName() + "], but that pkg does not exist -- not allowed");
                }

                if (!permissionEvaluator.hasPermission(
                        SecurityContextHolder.getContext().getAuthentication(),
                        requestPkgOptional.get(),
                        Permission.BULK_USERRATINGSPREADSHEETREPORT_PKG)) {
                    throw new AccessDeniedException(
                            "attempt to access a user rating report for pkg ["
                                    + request.getPkgName() + "], but this was disallowed");
                }

                spec.setPkgName(request.getPkgName());
            }
            else {
                if (!permissionEvaluator.hasPermission(
                        SecurityContextHolder.getContext().getAuthentication(),
                        null,
                        Permission.BULK_USERRATINGSPREADSHEETREPORT_ALL)) {
                    throw new AccessDeniedException("attempt to access a user rating report, but was unauthorized");
                }
            }
        }

        spec.setOwnerUserNickname(user.getNickname());

        return new QueueUserRatingSpreadsheetJobResult()
                .guid(jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED));
    }

}
