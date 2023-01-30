/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.google.common.collect.ImmutableList;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.haiku.haikudepotserver.CapturingMailSender;
import org.haiku.haikudepotserver.support.ClientIdentifierSupplier;
import org.haiku.haikudepotserver.support.NoopClientIdentifierSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.mail.MailSender;

import java.util.List;

@PropertySource(
        value = {
                "classpath:test-local.properties",
                "${config.properties:file-not-found.properties}"},
        ignoreResourceNotFound = true
)
public class TestBasicConfig {

    @Bean("messageSourceBaseNames")
    public List<String> messageSourceBaseNames() {
        return ImmutableList.of(
                "classpath:messages",
                "classpath:naturallanguagemessages"
        );
    }

    @Bean
    public ClientIdentifierSupplier clientIdentifierSupplier() {
        return new NoopClientIdentifierSupplier();
    }

    @Bean
    public MailSender mailSender() {
        return new CapturingMailSender();
    }

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

}
