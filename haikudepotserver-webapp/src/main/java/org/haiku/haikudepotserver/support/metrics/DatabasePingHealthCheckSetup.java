/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.metrics;

import com.codahale.metrics.health.HealthCheckRegistry;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;

public class DatabasePingHealthCheckSetup {

    private final static String HEALTH_CHECK_DATABASE = "org.haiku.haikudepotserver.database";

    @Resource
    private DataSource dataSource;

    @Resource
    private HealthCheckRegistry healthCheckRegistry;

    @PostConstruct
    public void init() {
        healthCheckRegistry.register(HEALTH_CHECK_DATABASE, new DatabasePingHealthCheck(dataSource));
    }

}
