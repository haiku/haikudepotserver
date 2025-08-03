/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.haiku.haikudepotserver.job.DbDistributedJobServiceImpl;
import org.haiku.haikudepotserver.job.NoopJobServiceImpl;
import org.haiku.haikudepotserver.job.jpa.JpaJobService;
import org.haiku.haikudepotserver.job.model.JobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.storage.PgDataStorageRepository;
import org.haiku.haikudepotserver.storage.PgDataStorageServiceImpl;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.Collection;

@Import({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        BasicConfig.class,
        TestBasicConfig.class
})
public class TestConfig {

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
            case "db" -> new DbDistributedJobServiceImpl(
                    transactionManager,
                    objectMapper,
                    dataStorageService,
                    jobRunners,
                    applicationEventPublisher,
                    jpaJobService
            );
            case "noop" -> new NoopJobServiceImpl();
            default -> throw new IllegalStateException("unknown job service type [%s]".formatted(type));
        };
    }

    @Bean
    public DataStorageService dataStorageService(PgDataStorageRepository repository) {
        return new PgDataStorageServiceImpl(repository, 262144);
    }

}
