/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset;

import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetMaintenanceJobSpecification;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

public class PasswordResetMaintenanceJobRunner extends AbstractJobRunner<PasswordResetMaintenanceJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PasswordResetMaintenanceJobRunner.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PasswordResetOrchestrationService passwordResetOrchestrationService;

    /**
     * <p>This method has been overridden in order to ensure that during start-up at least one
     * maintenance of the password reset is done.</p>
     */

    @Override
    public void run(JobOrchestrationService jobOrchestrationService, PasswordResetMaintenanceJobSpecification specification) {
        passwordResetOrchestrationService.deleteExpiredPasswordResetTokens();
    }

}
