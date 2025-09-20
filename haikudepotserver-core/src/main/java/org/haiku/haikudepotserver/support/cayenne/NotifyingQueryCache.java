/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.cache.QueryCache;
import org.apache.cayenne.cache.QueryCacheEntryFactory;
import org.apache.cayenne.di.Inject;
import org.apache.cayenne.query.QueryMetadata;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.support.eventing.model.NotifyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * <p>This is a wrapper for the regular Cayenne cache interface {@link QueryCache}
 * that will notify out to other instances of the application server when the
 * cache should be removed for some item.</p>
 *
 * <p>The class allows for an {@link #notifyControl} instance variable to be
 * configured. This is able to control if the notification should be sent. We don't
 * want to send the notification if a notification is already being processed from
 * remote because then it would simply send out and yoyo back and forward between
 * the servers echoing endlessly.</p>
 */

public class NotifyingQueryCache implements QueryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(NotifyingQueryCache.class);

    private final QueryCache delegate;

    private final NotifyService notifyService;

    private final QueryCacheRemoveEventNotifyControl notifyControl;

    // The @Inject here is to fit into the Cayenne dependency injection framework
    // which is a bit like Guice. It interfaces with the Spring world via
    // `PersistenceConfig`.
    public NotifyingQueryCache(
            @Inject QueryCache delegate,
            @Inject NotifyService notifyService,
            @Inject QueryCacheRemoveEventNotifyControl notifyControl
    ) {
        this.delegate = delegate;
        this.notifyService = notifyService;
        this.notifyControl = notifyControl;
    }

    @Override
    public List get(QueryMetadata metadata) {
        return delegate.get(metadata);
    }

    @Override
    public List get(QueryMetadata metadata, QueryCacheEntryFactory factory) {
        return delegate.get(metadata, factory);
    }

    @Override
    public void put(QueryMetadata metadata, List results) {
        delegate.put(metadata, results);
    }

    @Override
    public void remove(String key) {
        Preconditions.checkArgument(StringUtils.isNotBlank(key), "the key must be supplied");

        LOGGER.debug("remove [{}]", key);

        delegate.remove(key);

        if (notifyControl.isEnabled()) {
            LOGGER.debug("notifying remove [{}]", key);
            notifyService.publishEvent(
                    new QueryCacheRemoveEvent(
                            List.of(new QueryCacheRemoveEvent.KeyRemove(key))
                    )
            );
        }
    }

    @Override
    public void removeGroup(String groupKey) {
        Preconditions.checkArgument(StringUtils.isNotBlank(groupKey), "the group key must be supplied");

        LOGGER.debug("remove group [{}]", groupKey);

        delegate.removeGroup(groupKey);

        if (notifyControl.isEnabled()) {
            LOGGER.debug("notifying remove group [{}]", groupKey);
            notifyService.publishEvent(
                    new QueryCacheRemoveEvent(
                            List.of(new QueryCacheRemoveEvent.GroupRemove(groupKey))
                    )
            );
        }
    }

    @Override
    public void removeGroup(String groupKey, Class<?> keyType, Class<?> valueType) {
        Preconditions.checkArgument(StringUtils.isNotBlank(groupKey), "the group key must be supplied");
        Preconditions.checkArgument(null != keyType, "the key type must be supplied");
        Preconditions.checkArgument(null != valueType, "the value type must be supplied");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "remove group [{}] with key/value [{}/{}]",
                    groupKey,
                    keyType.getName(),
                    valueType.getName()
            );
        }

        delegate.removeGroup(groupKey, keyType, valueType);

        if (notifyControl.isEnabled()) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(
                        "notifying remove group [{}] with key/value [{}/{}]",
                        groupKey,
                        keyType.getName(),
                        valueType.getName()
                );
            }

            notifyService.publishEvent(
                    new QueryCacheRemoveEvent(
                            List.of(new QueryCacheRemoveEvent.GroupWithTypesRemove(
                                    groupKey,
                                    keyType.getName(),
                                    valueType.getName()
                            ))
                    )
            );
        }
    }

    @Override
    public void clear() {
        LOGGER.debug("clear");
        delegate.clear();
        if (notifyControl.isEnabled()) {
            LOGGER.debug("notifying clear");
            notifyService.publishEvent(
                    new QueryCacheRemoveEvent(
                            List.of(new QueryCacheRemoveEvent.ClearRemove())
                    )
            );
        }
    }

}
