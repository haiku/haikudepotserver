/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.QueueAuthorizationRulesSpreadsheetResult;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.security.model.AuthorizationRulesSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component("authorizationJobApiServiceV2")
public class AuthorizationJobApiService extends AbstractApiService {

    protected static Logger LOGGER = LoggerFactory.getLogger(AuthorizationJobApiService.class);

    private final PermissionEvaluator permissionEvaluator;
    private final JobService jobService;

    public AuthorizationJobApiService(
            PermissionEvaluator permissionEvaluator,
            JobService jobService) {
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public QueueAuthorizationRulesSpreadsheetResult queueAuthorizationRulesSpreadsheet() {
        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.AUTHORIZATION_CONFIGURE)) {
            String msg = "attempt to queue authorization spreadsheet without sufficient authorization";
            LOGGER.warn(msg);
            throw new AccessDeniedException(msg);
        }

        String guid = jobService.submit(
                new AuthorizationRulesSpreadsheetJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED);
        return new QueueAuthorizationRulesSpreadsheetResult().guid(guid);
    }

}
