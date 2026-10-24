/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.haiku.haikudepotserver.CapturingMailSender;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.autoconfigure.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.mail.MailSender;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * <p>This is the configuration included by most integration tests.</p>
 */

@Import({

        // Things that would typically be provided by SpringBoot.
        DataSourceAutoConfiguration.class,
        FlywayAutoConfiguration.class,

        // Core configurations to setup things like object-mappers and ORMs
        CoreBasicConfig.class,
        CorePersistenceConfig.class,

        // Test doubles for some support services such as the job service
        // where it's not typically under test from another service such as the
        // PkgService.
        CoreTestDoublesConfig.class
})
@PropertySource(value = "classpath:test-local.properties")
@ComponentScan(basePackages = "org.haiku.haikudepotserver")
public class CoreTestConfig {

    public final static List<String> MESSAGE_SOURCE_BASE_NAMES =
            List.of(
                    "classpath:messages",
                    "classpath:naturallanguagemessages"
            );

    @Bean("messageSourceBaseNames")
    public List<String> messageSourceBaseNames() {
        return MESSAGE_SOURCE_BASE_NAMES;
    }

}
