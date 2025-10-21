/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.DbDistributedJobServiceImpl;
import org.haiku.haikudepotserver.job.LocalJobServiceImpl;
import org.haiku.haikudepotserver.job.NoopJobServiceImpl;
import org.haiku.haikudepotserver.job.jpa.JpaJobService;
import org.haiku.haikudepotserver.job.model.JobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationLookupService;
import org.haiku.haikudepotserver.storage.PgDataStorageRepository;
import org.haiku.haikudepotserver.storage.PgDataStorageServiceImpl;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.haiku.haikudepotserver.support.ClientIdentifierSupplier;
import org.haiku.haikudepotserver.support.HttpRequestClientIdentifierSupplier;
import org.haiku.haikudepotserver.thymeleaf.Dialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collection;
import java.util.List;

@Import({BasicConfig.class, ScheduleConfig.class})
@Configuration
public class AppConfig {

    @Bean
    public ClientIdentifierSupplier clientIdentifierSupplier() {
        return new HttpRequestClientIdentifierSupplier();
    }

    @Bean
    public JobService jobService(
            @Value("${hds.jobservice.type:db}") String type,
            DataStorageService dataStorageService,
            Collection<JobRunner<?>> jobRunners,
            PlatformTransactionManager transactionManager,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher,
            JpaJobService jpaJobService
    ) {
        return switch (type) {
            case "noop" -> new NoopJobServiceImpl();
            case "local" -> new LocalJobServiceImpl(dataStorageService, jobRunners);
            case "db" -> new DbDistributedJobServiceImpl(
                    transactionManager,
                    objectMapper,
                    dataStorageService,
                    jobRunners,
                    applicationEventPublisher,
                    jpaJobService);
            default -> throw new IllegalStateException("unexpected job service type: " + type);
        };
    }

    @Bean
    public DataStorageService dataStorageService(
            @Value("${hds.storage.pg.part-size:262144}") Long partSize,
            PgDataStorageRepository repository
            ) {
        return new PgDataStorageServiceImpl(repository, partSize);
    }

    @Bean("messageSourceBaseNames")
    public List<String> messageSourceBaseNames() {
        return ImmutableList.of(
                "classpath:messages",
                "classpath:webmessages",
                "classpath:naturallanguagemessages"
        );
    }

    @Bean
    public Dialect processorDialect(
            ServerRuntime serverRuntime,
            PkgLocalizationLookupService pkgLocalizationLookupService,
            @Value("${hds.deployment.is-production:false}") Boolean isProduction,
            @Value("classpath:/spa1/js/index.txt") Resource singlePageApplicationJavaScriptIndexResource) {
        return new Dialect(
                serverRuntime,
                pkgLocalizationLookupService,
                isProduction,
                singlePageApplicationJavaScriptIndexResource);
    }

}
