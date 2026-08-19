/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.springframework.context.annotation.Import;

/**
 * <p>This is the configuration included by most integration tests.</p>
 */

@Import({
        TestBasicConfig.class,
        TestJobAndDataConfig.class,
        TestMessageSourceConfig.class,
        TestPropertySourceConfig.class
})
public class TestConfig {
}
