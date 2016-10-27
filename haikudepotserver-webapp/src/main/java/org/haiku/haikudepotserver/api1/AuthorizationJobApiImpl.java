/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.authorization.job.QueueAuthorizationRulesSpreadsheetRequest;
import org.haiku.haikudepotserver.api1.model.authorization.job.QueueAuthorizationRulesSpreadsheetResult;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.haiku.haikudepotserver.security.AuthorizationService;
import org.haiku.haikudepotserver.security.model.AuthorizationRulesSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Component
public class AuthorizationJobApiImpl extends AbstractApiImpl implements AuthorizationJobApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(AuthorizationJobApiImpl.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Override
    public QueueAuthorizationRulesSpreadsheetResult queueAuthorizationRulesSpreadsheet(QueueAuthorizationRulesSpreadsheetRequest request) {
        Preconditions.checkArgument(null!=request, "a request objects is required");

        final ObjectContext context = serverRuntime.getContext();

        User user = obtainAuthenticatedUser(context);

        if (!authorizationService.check(context, user, null, Permission.AUTHORIZATION_CONFIGURE)) {
            LOGGER.warn("attempt to queue authorization spreadsheet without sufficient authorization");
            throw new AuthorizationFailureException();
        }

        QueueAuthorizationRulesSpreadsheetResult result = new QueueAuthorizationRulesSpreadsheetResult();
        result.guid = jobOrchestrationService.submit(new AuthorizationRulesSpreadsheetJobSpecification(),
                JobOrchestrationService.CoalesceMode.QUEUEDANDSTARTED).orElse(null);
        return result;
    }

}
