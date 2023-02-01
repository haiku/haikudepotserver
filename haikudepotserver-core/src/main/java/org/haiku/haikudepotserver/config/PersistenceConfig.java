/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.DataChannelFilter;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.HaikuDepot;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgIconImage;
import org.haiku.haikudepotserver.dataobjects.PkgLocalization;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgUserRatingAggregate;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.RepositorySourceMirror;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserRating;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveGroupDataChannelFilter;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveGroupListener;
import org.haiku.haikudepotserver.support.cayenne.ServerRuntimeFactory;
import org.haiku.haikudepotserver.support.db.UserUsageConditionsInitializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.util.Collections;

public class PersistenceConfig {

    @Bean(initMethod = "init")
//    @DependsOn({"haikuDepotManagedDatabase"})
    public UserUsageConditionsInitializer userUsageConditionsInitializer(
            ServerRuntime serverRuntime,
            ObjectMapper objectMapper
    ) {
        return new UserUsageConditionsInitializer(serverRuntime, objectMapper);
    }

    @Bean
    public ServerRuntime serverRuntime(
            DataSource dataSource,
            @Value("${cayenne.query.cache.size:250}") Integer queryCacheSize
    ) {
        ServerRuntimeFactory factory = new ServerRuntimeFactory(dataSource, queryCacheSize);
        return factory.getObject();
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
