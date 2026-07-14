/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.haiku.haikudepotserver.CapturingMailSender;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.mail.MailSender;

/**
 * Most tests will include this through {@link TestConfig} or other
 * test configuration classes.
 */

@Import({
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,
        BasicConfig.class,
})
public class TestBasicConfig {

    /**
     * <p>This instance of {@link MeterRegistry} would be created by the
     * Actuator system in SpringBoot, but it is only setup in the web
     * application -- here an instance is created to support beans in the
     * testing.</p>
     */

    @Bean
    public MeterRegistry meterRegistry() {
        return new SimpleMeterRegistry();
    }

    @Bean
    public MailSender mailSender() {
        return new CapturingMailSender();
    }

}
