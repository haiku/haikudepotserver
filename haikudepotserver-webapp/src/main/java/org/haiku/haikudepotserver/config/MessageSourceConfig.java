/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import com.google.common.collect.ImmutableList;
import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * Configures the source of the key-value pairs for the localization.
 */

public class MessageSourceConfig {

    @Bean("messageSourceBaseNames")
    public List<String> messageSourceBaseNames() {
        return ImmutableList.of(
                "classpath:messages",
                "classpath:webmessages",
                "classpath:multipagemessages",
                "classpath:naturallanguagemessages"
        );
    }

}
