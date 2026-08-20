/*
 * Copyright 2018-2026 Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.model.WebResourcePathPrefixes;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.CacheControl;
import org.springframework.http.converter.HttpMessageConverters;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.time.Duration;

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

    private final MultipageWebResourceService multipageWebResourceService;

    public WebConfig(MultipageWebResourceService multipageWebResourceService) {
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {

        // Multi-page Application (SSR)

        WebResourcePathPrefixes multipagePathPrefixes = multipageWebResourceService.getPathPrefixes();

        registry
                .addResourceHandler("%s**".formatted(multipagePathPrefixes.js()))
                .addResourceLocations("classpath:/js/")
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));
        registry
                .addResourceHandler("%s**".formatted(multipagePathPrefixes.css()))
                .addResourceLocations("classpath:/css/")
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));
        registry
                .addResourceHandler("%s**".formatted(multipagePathPrefixes.img()))
                .addResourceLocations("classpath:/img/")
                .setCacheControl(CacheControl.maxAge(Duration.ofHours(1)));

        // Single Page Application (SPA)

        registry
                .addResourceHandler(String.format("/%s/**", WebConstants.SEGMENT_JS))
                .addResourceLocations("classpath:/spa1/js/")
                .setCachePeriod(0);
        registry
                .addResourceHandler(String.format("/%s/**", WebConstants.SEGMENT_CSS))
                .addResourceLocations("classpath:/spa1/css/");
        registry
                .addResourceHandler(String.format("/%s/**", WebConstants.SEGMENT_IMG))
                .addResourceLocations(
                        "classpath:/spa1/img/",
                        "classpath:/img/" // SPA also relies on this.
                );

        // Misc

        registry
                .addResourceHandler("favicon.ico")
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(1)))
                .addResourceLocations("classpath:/img/favicon.ico");
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
