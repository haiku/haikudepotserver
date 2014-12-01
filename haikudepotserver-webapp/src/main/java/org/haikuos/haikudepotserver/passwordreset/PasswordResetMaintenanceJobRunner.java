/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.passwordreset;

import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.passwordreset.model.PasswordResetMaintenanceJobSpecification;
import org.haikuos.haikudepotserver.support.job.AbstractJobRunner;
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
    public void run(PasswordResetMaintenanceJobSpecification specification) {
        passwordResetOrchestrationService.deleteExpiredPasswordResetTokens();
    }

}
