/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import org.springframework.context.annotation.PropertySource;

@PropertySource(
        value = {
                "classpath:test-local.properties",
                "${config.properties:file-not-found.properties}"},
        ignoreResourceNotFound = true
)
public class TestPropertySourceConfig {
}
