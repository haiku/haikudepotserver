/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import org.haiku.haikudepotserver.support.metrics.DatabasePingHealthCheck;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

public class MetricsConfig {

    private final static String HEALTH_CHECK_DATABASE = "org.haiku.haikudepotserver.database";

    @Bean
    public MetricRegistry metricRegistry() {
        return new MetricRegistry();
    }

    @Bean
    public HealthCheckRegistry healthCheckRegistry(DataSource dataSource) {
        HealthCheckRegistry registry = new HealthCheckRegistry();
        registry.register(HEALTH_CHECK_DATABASE, new DatabasePingHealthCheck(dataSource));
        return registry;
    }

}
