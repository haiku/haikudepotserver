/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.eventing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * <p>This class will take Spring application events and will relay them over to Postgres
 * using <code>NOTIFY</code> so that the {@link InterProcessEvent}s (wrapped in an instance
 * of {@link InterProcessEvent}) are sendable to other instances.</p>
 */

public class InterProcessEventPgNotifyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(InterProcessEventPgNotifyService.class);

    private static final String NOTIFY_STATEMENT = "SELECT pg_notify('hds.event', ?)";

    private final DataSource dataSource;

    private final ObjectMapper objectMapper;

    private final InterProcessEventPgConfig config;

    public InterProcessEventPgNotifyService(
            ObjectMapper objectMapper,
            DataSource dataSource,
            InterProcessEventPgConfig config) {
        this.dataSource = Preconditions.checkNotNull(dataSource);
        this.objectMapper = Preconditions.checkNotNull(objectMapper);
        this.config = config;
    }

    @EventListener
    public void onApplicationEvent(InterProcessEvent event) {
        if (null == event.getSourceIdentifier()) {
            event.setSourceIdentifier(config.getSourceIdentifier());
        }

        if (StringUtils.equals(config.getSourceIdentifier(), event.getSourceIdentifier())) {
            publishEvent(event);
        }
    }

    void publishEvent(InterProcessEvent event) {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(NOTIFY_STATEMENT)
        ) {
            statement.setString(1, objectMapper.writeValueAsString(event));
            statement.execute();
        } catch (IOException ioe) {
            throw new UncheckedIOException("failure to prepare payload for notify to pg", ioe);
        } catch (SQLException se) {
            LOGGER.error("unable to notify inter-process event to pg", se);
        }
    }

}
