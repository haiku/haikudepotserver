/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.passwordreset;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.support.AbstractLocalBackgroundProcessingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

public class PasswordResetMaintenanceService extends AbstractLocalBackgroundProcessingService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PasswordResetMaintenanceService.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PasswordResetOrchestrationService passwordResetOrchestrationService;

    /**
     * <p>This method has been overridden in order to ensure that during start-up at least one
     * maintenance of the password reset is done.</p>
     */

    @Override
    public void doStart() {
        super.doStart();
        submit();
    }

    public void submit() {
        Preconditions.checkState(null != executor, "the service is not running, but a job is being submitted");

        if(runnables.isEmpty()) {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    passwordResetOrchestrationService.deleteExpiredPasswordResetTokens();
                }
            });

            LOGGER.info("did submit job to perform password reset maintenance");
        }
        else {
            LOGGER.info("did not submit job to perform password reset maintenance; there was one already queued");
        }

    }

}
