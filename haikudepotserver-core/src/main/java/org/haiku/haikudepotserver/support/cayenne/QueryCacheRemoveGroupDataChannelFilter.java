/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.collect.Sets;
import org.apache.cayenne.*;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.graph.GraphDiff;
import org.apache.cayenne.query.Query;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Set;

/**
 * <p>This one hooks into the Cayenne "transaction" flow in order to pick up
 * all of the groups that need to be flushed at the end of the transaction.
 * </p>
 */

public class QueryCacheRemoveGroupDataChannelFilter implements DataChannelFilter {

    final static String KEY_QUERYCACHEREMOVEGROUPS = "org.haiku.haikudepotserver.QueryCacheRemoveNames";

    private final ServerRuntime serverRuntime;

    public QueryCacheRemoveGroupDataChannelFilter(ServerRuntime serverRuntime) {
        super();
        this.serverRuntime = serverRuntime;
    }

    @PostConstruct
    public void init() {
        serverRuntime.getDataDomain().addFilter(this);
    }

    // --------------
    // DataChannelFilter

    @Override
    public void init(DataChannel channel) {
    }

    @Override
    public QueryResponse onQuery(ObjectContext originatingContext, Query query, DataChannelFilterChain filterChain) {
        return filterChain.onQuery(originatingContext, query);
    }

    @Override
    public GraphDiff onSync(ObjectContext originatingContext, GraphDiff changes, int syncType, DataChannelFilterChain filterChain) {

        GraphDiff result = filterChain.onSync(originatingContext, changes, syncType);

        try {
            switch (syncType) {

                case DataChannel.FLUSH_NOCASCADE_SYNC:
                case DataChannel.FLUSH_CASCADE_SYNC:
                    @SuppressWarnings("unchecked")
                    Set<String> groups = (Set<String>) originatingContext.getUserProperty(KEY_QUERYCACHEREMOVEGROUPS);

                    if (null != groups) {
                        for (String group : groups) {
                            serverRuntime.getDataDomain().getQueryCache().removeGroup(group);
                        }
                    }

                    break;
            }
        }
        finally {
            originatingContext.setUserProperty(KEY_QUERYCACHEREMOVEGROUPS, Sets.newHashSet());
        }

        return result;
    }

}
