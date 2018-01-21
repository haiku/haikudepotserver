/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset.job;

import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.passwordreset.PasswordResetServiceImpl;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetMaintenanceJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PasswordResetMaintenanceJobRunner extends AbstractJobRunner<PasswordResetMaintenanceJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PasswordResetMaintenanceJobRunner.class);

    private final PasswordResetServiceImpl passwordResetService;

    public PasswordResetMaintenanceJobRunner(
            PasswordResetServiceImpl passwordResetService
    ) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * <p>This method has been overridden in order to ensure that during start-up at least one
     * maintenance of the password reset is done.</p>
     */

    @Override
    public void run(JobService jobService, PasswordResetMaintenanceJobSpecification specification) {
        passwordResetService.deleteExpiredPasswordResetTokens();
    }

}
