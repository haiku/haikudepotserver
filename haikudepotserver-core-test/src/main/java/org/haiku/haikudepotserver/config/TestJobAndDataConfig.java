/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.BulkDataJobCoordinatorServiceImpl;
import org.haiku.haikudepotserver.job.DbDistributedJob2ServiceImpl;
import org.haiku.haikudepotserver.job.NoopJobServiceImpl;
import org.haiku.haikudepotserver.job.model.BulkDataJobCoordinatorService;
import org.haiku.haikudepotserver.job.model.JobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.storage.PgDataStorageServiceImpl;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;
import java.time.Clock;
import java.time.Duration;
import java.util.Collection;

/**
 * <p>Configuration to put in place a default job and job data system that does nothing
 * so that jobs don't interfere with tests.</p>
 *
 * <p>The test {@code JobApiServiceIT} will have specific setup when the actual concrete
 * job service needs to be tested.</p>
 */

public class TestJobAndDataConfig {

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
    public BulkDataJobCoordinatorService bulkDataJobCoordinatorService(
            ServerRuntime serverRuntime,
            JobService jobService,
            RepositoryService repositoryService,
            PkgService pkgService,
            NaturalLanguageService naturalLanguageService
    ) {
        return new BulkDataJobCoordinatorServiceImpl(
                Clock.systemUTC(),
                serverRuntime,
                jobService,
                repositoryService,
                pkgService,
                naturalLanguageService,
                Duration.ofHours(1), // renewAfterRemainingDuration
                Duration.ofMinutes(10), // renewStanddownSinceLastStartDuration
                Duration.ofHours(4), // renewForNaturalLanguageDuration
                Duration.ofHours(1) // clearExpiredAfterFinishedDuration
        );
    }

    @Bean
    public DataStorageService dataStorageService(DataSource dataSource, MeterRegistry meterRegistry) {
        return new PgDataStorageServiceImpl(dataSource, meterRegistry, 262144);
    }

}
