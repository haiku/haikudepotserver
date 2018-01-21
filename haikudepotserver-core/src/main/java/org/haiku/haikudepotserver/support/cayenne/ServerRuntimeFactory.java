/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import com.google.common.base.Preconditions;
import org.apache.cayenne.cache.QueryCache;
import org.apache.cayenne.configuration.Constants;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.di.MapBuilder;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Value;

import javax.annotation.Resource;
import javax.sql.DataSource;

/**
 * <p>This factory will setup the Apache Cayenne ORM system.</p>
 */

public class ServerRuntimeFactory implements FactoryBean<ServerRuntime> {

    private final DataSource dataSource;
    private final Integer queryCacheSize;

    public ServerRuntimeFactory(
            DataSource dataSource,
            Integer queryCacheSize
    ) {
        this.dataSource = Preconditions.checkNotNull(dataSource);
        this.queryCacheSize = queryCacheSize;
    }

    @Override
    public ServerRuntime getObject() {
        return ServerRuntime.builder()
                .addConfigs("cayenne-haikudepotserver.xml")
                .dataSource(dataSource)
                .addModule(binder -> {
                    // remove at 4.0 final because the Map cache should be working again.
                    binder.bind(QueryCache.class).toInstance(
                            new org.haiku.haikudepotserver.support.cayenne.QueryCache(queryCacheSize));
                })
                .addModule(binder -> {
                    MapBuilder<Object> props = binder.bindMap(Constants.PROPERTIES_MAP);
                    props.put(Constants.SERVER_OBJECT_RETAIN_STRATEGY_PROPERTY, "weak"); // hard|soft|weak
                    props.put(Constants.SERVER_CONTEXTS_SYNC_PROPERTY, "true");
                    props.put(Constants.QUERY_CACHE_SIZE_PROPERTY, queryCacheSize.toString());
                })
                .build();
    }

    @Override
    public Class<?> getObjectType() {
        return ServerRuntime.class;
    }

    @Override
    public boolean isSingleton() {
        return true;
    }

}
