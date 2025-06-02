/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.haiku.haikudepotserver.job.LocalJobServiceImpl;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.storage.PgDataStorageRepository;
import org.haiku.haikudepotserver.storage.PgDataStorageServiceImpl;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@Import({
        DataSourceAutoConfiguration.class,
        DataSourceTransactionManagerAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        BasicConfig.class,
        TestBasicConfig.class
})
public class TestConfig {

    @Bean
    public JobService localJobService(DataStorageService dataStorageService) {
        return new LocalJobServiceImpl(dataStorageService);
    }

    @Bean
    public DataStorageService dataStorageService(PgDataStorageRepository repository) {
        return new PgDataStorageServiceImpl(repository, 262144);
    }

}
