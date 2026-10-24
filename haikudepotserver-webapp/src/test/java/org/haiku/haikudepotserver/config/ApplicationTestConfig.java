/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.test.context.TestPropertySource;

@Import(value = {

        // Setup that would be typically provided by Spring Boot
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,

        // Core material providing the basics.
        CoreBasicConfig.class,
        CorePersistenceConfig.class,

        // Test doubles for some support services such as the job service
        // where it's not typically under test from another service such as the
        // PkgService.
        CoreTestDoublesConfig.class,

        ApplicationServletConfig.class,
        ApplicationWebConfig.class,
        ApplicationWebSecurityConfig.class,
        ApplicationWebMvcConfig.class,
        ApplicationMultipageConfig.class,
        ApplicationMessageSourceConfig.class

})
@ComponentScan(basePackages = "org.haiku.haikudepotserver")
public class ApplicationTestConfig {
}
