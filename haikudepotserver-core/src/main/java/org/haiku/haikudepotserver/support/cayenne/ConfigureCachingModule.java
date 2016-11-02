/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.cayenne;

import org.apache.cayenne.configuration.Constants;
import org.apache.cayenne.di.Binder;
import org.apache.cayenne.di.MapBuilder;
import org.apache.cayenne.di.Module;

/**
 * <p>This object / module is used as part of the configuration of the Cayenne ORM system.</p>
 */

public class ConfigureCachingModule implements Module {

    private Integer queryCacheSize;

    public Integer getQueryCacheSize() {
        return queryCacheSize;
    }

    public void setQueryCacheSize(Integer queryCacheSize) {
        this.queryCacheSize = queryCacheSize;
    }

    @Override
    public void configure(Binder binder) {

        MapBuilder<Object> props = binder.bindMap(Constants.PROPERTIES_MAP);

        props.put(Constants.SERVER_OBJECT_RETAIN_STRATEGY_PROPERTY, "weak"); // hard|soft|weak
        props.put(Constants.SERVER_CONTEXTS_SYNC_PROPERTY, "true");

        if(null!=queryCacheSize) {
            props.put(Constants.QUERY_CACHE_SIZE_PROPERTY, queryCacheSize.toString());
        }

    }

}
