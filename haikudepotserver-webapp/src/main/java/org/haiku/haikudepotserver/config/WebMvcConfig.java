/*
 * Copyright 2024 Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.config;

import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.DelegatingWebMvcConfiguration;

// see comment here;
// https://github.com/spring-projects/spring-framework/issues/25290#issuecomment-802218753

@Configuration
public class WebMvcConfig extends DelegatingWebMvcConfiguration {

    private final NaturalLanguageService naturalLanguageService;

    public WebMvcConfig(NaturalLanguageService naturalLanguageService) {
        this.naturalLanguageService = naturalLanguageService;
    }

    @Bean
    @Override
    public LocaleResolver localeResolver() {
        return new org.haiku.haikudepotserver.support.web.LocaleResolver(naturalLanguageService);
    }

}
