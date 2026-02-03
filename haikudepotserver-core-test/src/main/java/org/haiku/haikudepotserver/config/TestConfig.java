/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.DbDistributedJob2ServiceImpl;
import org.haiku.haikudepotserver.job.NoopJobServiceImpl;
import org.haiku.haikudepotserver.job.model.JobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.storage.PgDataStorageServiceImpl;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.util.Collection;

@Import({
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        BasicConfig.class,
        TestBasicConfig.class
})
public class TestConfig {

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
            case "db2" -> new DbDistributedJob2ServiceImpl(
                    serverRuntime,
                    objectMapper,
                    dataStorageService,
                    jobRunners,
                    applicationEventPublisher);
            case "noop" -> new NoopJobServiceImpl();
            default -> throw new IllegalStateException("unknown job service type [%s]".formatted(type));
        };
    }

    @Bean
    public DataStorageService dataStorageService(DataSource dataSource, MeterRegistry meterRegistry) {
        return new PgDataStorageServiceImpl(dataSource, meterRegistry, 262144);
    }

}
