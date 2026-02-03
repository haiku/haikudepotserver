/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.DataChannelFilter;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.cache.QueryCache;
import org.apache.cayenne.configuration.Constants;
import org.apache.cayenne.configuration.server.ServerModule;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.di.MapBuilder;
import org.apache.cayenne.velocity.VelocityModule;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.support.cayenne.NotifyingQueryCache;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveEventNotifyControl;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveGroupDataChannelFilter;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveGroupListener;
import org.haiku.haikudepotserver.support.db.UserUsageConditionsInitializer;
import org.haiku.haikudepotserver.support.eventing.model.NotifyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.sql.init.dependency.DependsOnDatabaseInitialization;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.Collections;

public class PersistenceConfig {

    @Bean(initMethod = "init")
    public UserUsageConditionsInitializer userUsageConditionsInitializer(
            ServerRuntime serverRuntime,
            ObjectMapper objectMapper
    ) {
        return new UserUsageConditionsInitializer(serverRuntime, objectMapper);
    }

    // -------------------------------------
    // CAYENNE CORE

    @Bean
    @DependsOnDatabaseInitialization
    public ServerRuntime serverRuntime(
            DataSource dataSource,
            @Value("${cayenne.query.cache.size:250}") Integer queryCacheSize,
            NotifyService notifyService,
            QueryCacheRemoveEventNotifyControl notifyControl
    ) {
        // This is the Cayenne-bespoke dependency injection framework interfacing with the Spring one. The
        // Cayenne one works a little bit like Juice.
        return ServerRuntime.builder()
                .addConfigs("cayenne-haikudepotserver.xml")
                .dataSource(dataSource)
                .addModule(new ServerModule())
                .addModule(new VelocityModule())
                .addModule(binder -> binder
                        .bind(QueryCacheRemoveEventNotifyControl.class)
                        .toInstance(notifyControl))
                .addModule(binder -> binder
                        .bind(NotifyService.class)
                        .toInstance(notifyService))
                .addModule(binder -> binder
                        .decorate(QueryCache.class)
                        .after(NotifyingQueryCache.class))
                .addModule(binder -> {
                    MapBuilder<Object> props = binder.bindMap(Object.class, Constants.PROPERTIES_MAP);
                    props.put(Constants.SERVER_OBJECT_RETAIN_STRATEGY_PROPERTY, "weak"); // hard|soft|weak
                    props.put(Constants.SERVER_CONTEXTS_SYNC_PROPERTY, "true");
                    props.put(Constants.QUERY_CACHE_SIZE_PROPERTY, queryCacheSize.toString());
                })
                .build();
    }

    // -------------------------------------
    // CAYENNE LIFECYCLE

    /**
     * <p>Setup for query cache removal.  The filter will install itself into the Cayenne runtime
     * and the listeners will keep track of which query cache groups are to be dropped.</p>
     */

    @Bean
    public DataChannelFilter queryCacheRemoveGroupDataChannelFilter(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupDataChannelFilter(serverRuntime);
    }

    @Bean
    public LifecycleListener naturalLanguageQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime,
                ImmutableList.of(UserRating.class, PkgVersionLocalization.class),
                Collections.singletonList(HaikuDepot.CacheGroup.NATURAL_LANGUAGE.name()));
    }

    @Bean
    public LifecycleListener userUsageConditionsQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(
                serverRuntime, UserUsageConditions.class, HaikuDepot.CacheGroup.USER_USAGE_CONDITIONS.name());
    }

    @Bean
    public LifecycleListener userQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(
                serverRuntime, User.class, HaikuDepot.CacheGroup.USER.name());
    }

    @Bean
    public LifecycleListener pkgUserRatingAggregateQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime, PkgUserRatingAggregate.class,
                HaikuDepot.CacheGroup.PKG_USER_RATING_AGGREGATE.name());
    }

    @Bean
    public LifecycleListener pkgQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(
                serverRuntime,
                ImmutableList.of(
                        Pkg.class,
                        PkgVersion.class,
                        PkgSupplement.class),
                Collections.singletonList(HaikuDepot.CacheGroup.PKG.name()));
    }

    @Bean
    public LifecycleListener repositoryQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(
                serverRuntime,
                ImmutableList.of(
                        Repository.class,
                        RepositorySource.class,
                        RepositorySourceMirror.class),
                Collections.singletonList(HaikuDepot.CacheGroup.REPOSITORY.name()));
    }

    @Bean
    public LifecycleListener pkgIconQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime,
                ImmutableList.of(PkgIcon.class, PkgIconImage.class),
                Collections.singletonList(HaikuDepot.CacheGroup.PKG_ICON.name()));
    }

    @Bean
    public LifecycleListener pkgLocalizationQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime, PkgLocalization.class,
                HaikuDepot.CacheGroup.PKG_LOCALIZATION.name());
    }

    @Bean
    public LifecycleListener pkgVersionLocalizationQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime, PkgVersionLocalization.class,
                HaikuDepot.CacheGroup.PKG_VERSION_LOCALIZATION.name());
    }


}
