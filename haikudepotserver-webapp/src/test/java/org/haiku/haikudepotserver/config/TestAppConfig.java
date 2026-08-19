/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;

@PropertySource(
        value = {
                "classpath:web-test-local.properties",
                "classpath:test-local.properties",
                "${config.properties:file-not-found.properties}"},
        ignoreResourceNotFound = true
)
@Import(value = {

        // core test
        TestBasicConfig.class,
        TestJobAndDataConfig.class,

        MultipageAppConfig.class,
        MessageSourceConfig.class
})
public class TestAppConfig {
}
