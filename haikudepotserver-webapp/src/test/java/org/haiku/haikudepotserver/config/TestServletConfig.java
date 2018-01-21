/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Import;

@Import(value = {ServletConfig.class})
@ComponentScan(basePackages = { "org.haiku.haikudepotserver" })
public class TestServletConfig {
}
