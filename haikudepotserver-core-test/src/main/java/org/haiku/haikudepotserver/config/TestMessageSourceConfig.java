/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import org.springframework.context.annotation.Bean;

import java.util.List;

/**
 * <p>Configures the sources for key-value pairs for localization used during the
 * core tests.</p>
 */

public class TestMessageSourceConfig {

    @Bean("messageSourceBaseNames")
    public List<String> messageSourceBaseNames() {
        return List.of(
                "classpath:messages",
                "classpath:naturallanguagemessages"
        );
    }

}
