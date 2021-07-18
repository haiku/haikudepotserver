/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.authorization.job.QueueAuthorizationRulesSpreadsheetRequest;
import org.haiku.haikudepotserver.api1.model.authorization.job.QueueAuthorizationRulesSpreadsheetResult;
import org.haiku.haikudepotserver.dataobjects.User;
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

@Component("authorizationJobApiImplV1")
@AutoJsonRpcServiceImpl
public class AuthorizationJobApiImpl extends AbstractApiImpl implements AuthorizationJobApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(AuthorizationJobApiImpl.class);

    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;
    private final JobService jobService;

    public AuthorizationJobApiImpl(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    @Override
    public QueueAuthorizationRulesSpreadsheetResult queueAuthorizationRulesSpreadsheet(QueueAuthorizationRulesSpreadsheetRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");

        final ObjectContext context = serverRuntime.newContext();

        User user = obtainAuthenticatedUser(context);

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.AUTHORIZATION_CONFIGURE)) {
            String msg = "attempt to queue authorization spreadsheet without sufficient authorization";
            LOGGER.warn(msg);
            throw new AccessDeniedException(msg);
        }

        QueueAuthorizationRulesSpreadsheetResult result = new QueueAuthorizationRulesSpreadsheetResult();
        result.guid = jobService.submit(new AuthorizationRulesSpreadsheetJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED);
        return result;
    }

}
