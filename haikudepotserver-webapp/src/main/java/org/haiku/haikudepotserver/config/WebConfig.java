/*
 * Copyright 2018-2026 Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

//@EnableWebMvc <-- is done manually so that locales can be setup
@EnableWebSecurity
@ComponentScan(
        basePackages = { "org.haiku.haikudepotserver" },
        useDefaultFilters = false,
        includeFilters = {
                @ComponentScan.Filter(Controller.class)
        }
)
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler(WebConstants.ANT_PATTERN_JS).addResourceLocations(
                        "classpath:/spa1/js/",
                        "classpath:/js/"
                       )
                .setCachePeriod(0);
        registry.addResourceHandler(WebConstants.ANT_PATTERN_CSS).addResourceLocations(
                "classpath:/spa1/css/",
                "classpath:/css/");
        registry.addResourceHandler(WebConstants.ANT_PATTERN_IMG).addResourceLocations(
                "classpath:/spa1/img/",
                "classpath:/img/");
        registry.addResourceHandler("favicon.ico").addResourceLocations("classpath:/img/favicon.ico");
    }

    @Override
    public void configureMessageConverters(HttpMessageConverters.ServerBuilder builder) {
        builder.configureMessageConvertersList(converters -> {
            JacksonJsonHttpMessageConverter messageConverter = new JacksonJsonHttpMessageConverter();
            for (int i = 0; i < converters.size(); i++) {
                if (converters.get(i) instanceof JacksonJsonHttpMessageConverter) {
                    converters.set(i, messageConverter);
                    return;
                }
            }
            converters.add(messageConverter);
        });
    }

}
