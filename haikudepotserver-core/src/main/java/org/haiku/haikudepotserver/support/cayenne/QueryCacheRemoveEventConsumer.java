/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.cache.QueryCache;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessEvent;

import java.util.function.Consumer;

public class QueryCacheRemoveEventConsumer implements Consumer<InterProcessEvent> {

    private final ServerRuntime serverRuntime;

    private final QueryCacheRemoveEventNotifyControl notifyControl;

    public QueryCacheRemoveEventConsumer(ServerRuntime serverRuntime, QueryCacheRemoveEventNotifyControl notifyControl) {
        Preconditions.checkArgument(null != serverRuntime);
        Preconditions.checkArgument(null != notifyControl);
        this.serverRuntime = serverRuntime;
        this.notifyControl = notifyControl;
    }

    @Override
    public void accept(InterProcessEvent interProcessEvent) {
        if (interProcessEvent instanceof QueryCacheRemoveEvent queryCacheRemoveEvent) {
            try {
                // Disable the downstream notify because if we get an event in then it is not necessary to send the
                // event back out again.
                notifyControl.disable();
                queryCacheRemoveEvent.getRemoves().forEach(this::handleRemove);
            } finally {
                notifyControl.enable();
            }
        }
    }

    public void handleRemove(QueryCacheRemoveEvent.Remove remove) {
        Preconditions.checkArgument(null != remove, "the remove must be supplied");
        QueryCache queryCache = serverRuntime.getDataDomain().getQueryCache();

        switch (remove) {
            case QueryCacheRemoveEvent.ClearRemove clearRemove:
                queryCache.clear();
                break;
            case QueryCacheRemoveEvent.KeyRemove keyRemove:
                queryCache.remove(keyRemove.getKey());
                break;
            case QueryCacheRemoveEvent.GroupRemove groupRemove:
                queryCache.removeGroup(groupRemove.getGroupKey());
                break;
            case QueryCacheRemoveEvent.GroupWithTypesRemove groupWithTypesRemove:
                try {
                    queryCache.removeGroup(
                            groupWithTypesRemove.getGroupKey(),
                            this.getClass().getClassLoader().loadClass(groupWithTypesRemove.getKeyTypeClassName()),
                            this.getClass().getClassLoader().loadClass(groupWithTypesRemove.getKeyTypeClassName())
                    );
                } catch (ClassNotFoundException cnfe) {
                    throw new IllegalStateException("unable to find the class for a cache removal", cnfe);
                }
                break;
        }
    }

}
