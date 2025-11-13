/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.eventing;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.AbstractExecutionThreadService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessEvent;
import org.postgresql.PGConnection;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryListener;
import org.springframework.retry.backoff.ExponentialBackOffPolicy;
import org.springframework.retry.policy.AlwaysRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.function.Consumer;

/**
 * <p>This class listens to Postgres' PUBLISH/NOTIFY and relays any messages that are
 * published into the Spring Event bus.</p>
 */

public class InterProcessEventPgListenService extends AbstractExecutionThreadService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterProcessEventPgListenService.class);

    private static final String STATEMENT_LISTEN = "LISTEN \"hds.event\"";

    private static final int LISTEN_TIMEOUT_MILLIS = 2 * 1000;

    private final DataSource dataSource;

    private final ObjectMapper objectMapper;

    private final InterProcessEventPgConfig config;

    private final Consumer<InterProcessEvent> handler;

    public InterProcessEventPgListenService(
            ObjectMapper objectMapper,
            DataSource dataSource,
            InterProcessEventPgConfig config,
            Consumer<InterProcessEvent> handler) {
        this.objectMapper = Preconditions.checkNotNull(objectMapper);
        this.dataSource = Preconditions.checkNotNull(dataSource);
        this.config = Preconditions.checkNotNull(config);
        this.handler = Preconditions.checkNotNull(handler);
    }

    /**
     * <p>This will listen for events from the database on a thread.</p>
     */
    @PostConstruct
    public void init() {
        startAsync();
        awaitRunning();
    }

    @PreDestroy
    public void tearDown() {
        stopAsync();
        awaitTerminated();
    }

    @Override
    protected String serviceName() {
        return this.getClass().getSimpleName();
    }

    private RetryTemplate createRetryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();

        ExponentialBackOffPolicy backOffPolicy = new ExponentialBackOffPolicy();
        backOffPolicy.setInitialInterval(500L);
        backOffPolicy.setMaxInterval(30000L);

        retryTemplate.setBackOffPolicy(backOffPolicy);
        retryTemplate.setRetryPolicy(new AlwaysRetryPolicy());
        retryTemplate.setListeners(new RetryListener[]{
           new RetryListener() {
               @Override
               public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
                   RetryListener.super.onError(context, callback, throwable);
                   LOGGER.error("failed to retry", throwable);
               }
           }
        });

        return retryTemplate;
    }

    @Override
    protected void run() throws Exception {
        listenForPgEvents();
    }

    private void listenForPgEvents() {
        RetryTemplate retryTemplate = createRetryTemplate();

        try {
            retryTemplate.execute((RetryCallback<Object, Throwable>) context -> {
                tryListenForPgEvents();
                return Boolean.TRUE;
            });
        } catch (Throwable e) {
            LOGGER.error("failure to retry listening for pg events", e);
        }
    }

    private void tryListenForPgEvents() throws SQLException, JsonProcessingException {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            // ^ The notifys don't work via the PG driver unless it's non transactional.

            try (Statement statement = connection.createStatement()) {
                statement.execute(STATEMENT_LISTEN);
            }

            listenForPgEvents(connection.unwrap(PGConnection.class));
        }
    }

    private void listenForPgEvents(PGConnection pgConnection) throws SQLException, JsonProcessingException {
        LOGGER.info("starting listening for pg events");

        while (true) {
            switch (state()) {
                case NEW, STARTING, RUNNING:
                    break;
                default:
                    LOGGER.info("stopped listening for pg events");
                    return;
            }

            org.postgresql.PGNotification[] notifications = pgConnection.getNotifications(LISTEN_TIMEOUT_MILLIS);

            for (org.postgresql.PGNotification notification : notifications) {
                handleInterProcessEvent(notification.getParameter());
            }
        }
    }

    private void handleInterProcessEvent(String notificationParameter) throws JsonProcessingException {
        InterProcessEvent event = objectMapper.readValue(notificationParameter, InterProcessEvent.class);

        if (StringUtils.isEmpty(event.getSourceIdentifier())) {
            LOGGER.warn("an event has arrived with no source identifier --> ignore");
            return;
        }

        if (!StringUtils.equals(config.getSourceIdentifier(), event.getSourceIdentifier())) {
            handler.accept(event);
        }
    }
}
