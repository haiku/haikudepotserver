/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.deployment;

import org.haiku.haikudepotserver.deployment.model.ShutdownAllInstancesEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * <p>Service to manage the deployment. It is able to source information about the deployment
 * and cause shutdowns.</p>
 */

@Service
public class DeploymentManagementService {

    protected static Logger LOGGER = LoggerFactory.getLogger(DeploymentManagementService.class);

    private final static long DELAY_SHUTDOWN_LOCAL_SECS = 5;

    private final ApplicationContext applicationContext;
    private final ApplicationEventPublisher applicationEventPublisher;

    private ScheduledExecutorService executorService;

    public DeploymentManagementService(
            ApplicationEventPublisher applicationEventPublisher,
            ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        this.applicationEventPublisher = applicationEventPublisher;
    }

    /**
     * <p>Calling this method will fire a message to all Applications so that they all shutdown.</p>
     */

    public void initiateShutdownAllInstances() {
        LOGGER.info("will request shutdown all instances");
        applicationEventPublisher.publishEvent(new ShutdownAllInstancesEvent());
    }

    /**
     * <p>This method will cause this Application to shutdown.</p>
     */

    private void shutdownInstance() {
        switch (applicationContext) {
            case ConfigurableApplicationContext cac:
                LOGGER.info("will initiate instance shutdown");
                cac.close();
                break;
            default:
                throw new IllegalStateException(
                        "unexpected application context class["
                                + applicationContext.getClass().getSimpleName()
                                + "]");

        }
    }

    @EventListener
    public void onApplicationEvent(ShutdownAllInstancesEvent event) {

        // If the event arrived via a distributed message then shutting-down the Application
        // while it is processing the message ends up causing problems for the shutdown.

        if (null == executorService) {
            executorService = Executors.newSingleThreadScheduledExecutor();
            executorService.schedule(this::shutdownInstance, DELAY_SHUTDOWN_LOCAL_SECS, TimeUnit.SECONDS);
            executorService.shutdown();
        }
        LOGGER.info("will shutdown this instance in {} seconds", DELAY_SHUTDOWN_LOCAL_SECS);
    }

}
