/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.DbDistributedJob2ServiceImpl;
import org.haiku.haikudepotserver.job.LocalJobServiceImpl;
import org.haiku.haikudepotserver.job.NoopJobServiceImpl;
import org.haiku.haikudepotserver.job.model.JobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationLookupService;
import org.haiku.haikudepotserver.storage.PgDataStorageServiceImpl;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.haiku.haikudepotserver.thymeleaf.Dialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.Resource;

import javax.sql.DataSource;
import java.util.Collection;
import java.util.List;

@Import({BasicConfig.class, ScheduleConfig.class})
@Configuration
public class AppConfig {

    @Bean
    public JobService jobService(
            @Value("${hds.jobservice.type:db2}") String type,
            ServerRuntime serverRuntime,
            DataStorageService dataStorageService,
            Collection<JobRunner<?>> jobRunners,
            ObjectMapper objectMapper,
            ApplicationEventPublisher applicationEventPublisher
    ) {
        return switch (type) {
            case "noop" -> new NoopJobServiceImpl();
            case "local" -> new LocalJobServiceImpl(dataStorageService, jobRunners);
            case "db2" -> new DbDistributedJob2ServiceImpl(
                    serverRuntime,
                    objectMapper,
                    dataStorageService,
                    jobRunners,
                    applicationEventPublisher);
            default -> throw new IllegalStateException("unexpected job service type: " + type);
        };
    }

    @Bean
    public DataStorageService dataStorageService(
            DataSource dataSource,
            MeterRegistry meterRegistry,
            @Value("${hds.storage.pg.part-size:262144}") Long partSize
    ) {
        return new PgDataStorageServiceImpl(dataSource, meterRegistry, partSize);
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
