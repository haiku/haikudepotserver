/*
 * Copyright 2018-2024 Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

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
        registry.addResourceHandler(WebConstants.ANT_PATTERN_JS).addResourceLocations("classpath:/spa1/js/").setCachePeriod(0);
        registry.addResourceHandler(WebConstants.ANT_PATTERN_CSS).addResourceLocations("classpath:/spa1/css/");
        registry.addResourceHandler(WebConstants.ANT_PATTERN_IMG).addResourceLocations(
                "classpath:/spa1/img/",
                "classpath:/img/");
        registry.addResourceHandler("favicon.ico").addResourceLocations("classpath:/img/favicon.ico");
    }

    @Override
    public void extendMessageConverters(List<HttpMessageConverter<?>> converters) {
        ObjectMapper objectMapper = new ObjectMapperFactory().getObject();
        MappingJackson2HttpMessageConverter messageConverter = new MappingJackson2HttpMessageConverter(objectMapper);
        for (int i = 0; i < converters.size(); i++) {
            if (converters.get(i) instanceof MappingJackson2HttpMessageConverter) {
                converters.set(i, messageConverter);
                return;
            }
        }
        converters.add(messageConverter);
    }
}
