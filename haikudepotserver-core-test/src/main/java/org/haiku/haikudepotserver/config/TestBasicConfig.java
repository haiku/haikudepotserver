/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.google.common.collect.ImmutableList;
import org.haiku.haikudepotserver.CapturingMailSender;
import org.haiku.haikudepotserver.support.ClientIdentifierSupplier;
import org.haiku.haikudepotserver.support.NoopClientIdentifierSupplier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.PropertySource;
import org.springframework.mail.MailSender;

import java.util.List;

@PropertySource(
        value = {"classpath:test-local.properties", "${config.properties:}"},
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

}
