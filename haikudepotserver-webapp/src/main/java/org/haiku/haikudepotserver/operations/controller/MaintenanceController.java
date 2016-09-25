/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.operations.controller;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetMaintenanceJobSpecification;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.haiku.haikudepotserver.repository.model.PkgRepositoryImportJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>This controller takes care of scheduling maintenance tasks.  Invocations come into here from a CURL
 * or similar request driven by cron.</p>
 */

@Controller
@RequestMapping(path = {
        "/maintenance", // TODO; remove
        "/__maintenance" })
public class MaintenanceController {

    protected static Logger LOGGER = LoggerFactory.getLogger(MaintenanceController.class);

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Resource
    private ServerRuntime serverRuntime;

    /**
     * <p>This triggers daily tasks.</p>
     */

    @RequestMapping(value = "/daily", method = RequestMethod.GET)
    public void daily(
            HttpServletResponse response) throws IOException {

        // go through all of the repositories and fetch them.  This is essentially a mop-up
        // task for those repositories that are unable to trigger a refresh.

        {
            ObjectContext context = serverRuntime.getContext();

            for(Repository repository : Repository.getAllActive(context)) {
                jobOrchestrationService.submit(
                        new PkgRepositoryImportJobSpecification(repository.getCode()),
                        JobOrchestrationService.CoalesceMode.QUEUED);
            }
        }

        LOGGER.info("did trigger daily maintenance");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
        response.getWriter().print("accepted request for daily maintenance");

    }

    /**
     * <p>This triggers hourly tasks.</p>
     */

    // TODO; remove "mediumterm".

    @RequestMapping(path = { "/mediumterm", "/hourly" }, method = RequestMethod.GET)
    public void hourly(
            HttpServletResponse response) throws IOException {

        // remove any jobs which are too old and are no longer required.

        jobOrchestrationService.clearExpiredJobs();

        // remove any expired password reset tokens.

        {
            if (UserPasswordResetToken.hasAny(serverRuntime.getContext())) {
                jobOrchestrationService.submit(
                        new PasswordResetMaintenanceJobSpecification(),
                        JobOrchestrationService.CoalesceMode.QUEUEDANDSTARTED);
            }
            else {
                LOGGER.debug("did not submit task for password reset maintenance as there are no tokens stored");
            }
        }

        LOGGER.info("did trigger hourly maintenance");

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
        response.getWriter().print("accepted request for hourly maintenance");

    }

}
