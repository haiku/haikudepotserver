/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.google.common.collect.ImmutableList;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.cayenne.DataChannelFilter;
import org.apache.cayenne.LifecycleListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveGroupDataChannelFilter;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveGroupListener;
import org.haiku.haikudepotserver.support.cayenne.ServerRuntimeFactory;
import org.haiku.haikudepotserver.support.db.migration.ManagedDatabase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Collections;

public class PersistenceConfig {

    @Bean
    public DataSource dataSource(
            @Value("${jdbc.driver}") String driverClassName,
            @Value("${jdbc.url}") String jdbcUrl,
            @Value("${jdbc.username}") String username,
            @Value("${jdbc.password}") String password,
            @Value("${jdbc.pool.maximumpoolsize:8}") Integer maximumPoolSize,
            @Value("${jdbc.pool.minimumidle:1}") Integer minimumIdle
    ) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(jdbcUrl);
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        return dataSource;
    }

    @Bean(initMethod = "init")
    public ManagedDatabase haikuDepotManagedDatabase(
            DataSource dataSource,
            @Value("${flyway.migrate}") Boolean flywayMigrate,
            @Value("${flyway.validateOnMigrate:true}") Boolean validateOnMigrate
    ) {
        return createManagedDatabase(dataSource, "haikudepot", flywayMigrate, validateOnMigrate);
    }

    @Bean(initMethod = "init")
    public ManagedDatabase captchaManagedDatabase(
            DataSource dataSource,
            @Value("${flyway.migrate}") Boolean flywayMigrate,
            @Value("${flyway.validateOnMigrate:true}") Boolean validateOnMigrate
    ) {
        return createManagedDatabase(dataSource, "captcha", flywayMigrate, validateOnMigrate);
    }

    private ManagedDatabase createManagedDatabase(
            DataSource dataSource,
            String schema,
            Boolean flywayMigrate,
            Boolean validateOnMigrate) {
        ManagedDatabase managedDatabase = new ManagedDatabase();
        managedDatabase.setDataSource(dataSource);
        managedDatabase.setMigrate(flywayMigrate);
        managedDatabase.setSchema(schema);
        managedDatabase.setValidateOnMigrate(validateOnMigrate);
        return managedDatabase;
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
    public LifecycleListener userQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime, User.class, HaikuDepot.CacheGroup.USER.name());
    }

    @Bean
    public LifecycleListener pkgUserRatingAggregateQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime, PkgUserRatingAggregate.class,
                HaikuDepot.CacheGroup.PKG_USER_RATING_AGGREGATE.name());
    }

    @Bean
    public LifecycleListener pkgQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime, Pkg.class, HaikuDepot.CacheGroup.PKG.name());
    }

    @Bean
    public LifecycleListener repositoryQueryCacheRemoveGroupListener(ServerRuntime serverRuntime) {
        return new QueryCacheRemoveGroupListener(serverRuntime,
                ImmutableList.of(Repository.class, RepositorySource.class),
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
