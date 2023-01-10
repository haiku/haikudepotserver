/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.haiku.haikudepotserver.api1.support.ObjectMapperFactory;
import org.haiku.haikudepotserver.metrics.MetricsInterceptor;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewResolverRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import java.util.List;
import java.util.stream.Stream;

@EnableWebMvc
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
        registry.addResourceHandler(WebConstants.ANT_PATTERN_DOCS).addResourceLocations("classpath:/docs/");
        registry.addResourceHandler("favicon.ico").addResourceLocations("classpath:/img/favicon.ico");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

//        registry
//                .addInterceptor(new LocaleChangeInterceptor())
//                .addPathPatterns(MultipageConstants.PATH_MULTIPAGE + "/**");

        // This is a seemingly ad-hoc list of things to monitor, but it is
        // unfortunately so that some things need to be wild-carded and others
        // not -- it is not entirely systematic.

        Stream.of(

                // these are crude aggregated paths for API v2
                "/__api/v2/authorization/**",
                "/__api/v2/authorization-job/**",
                "/__api/v2/captcha/**",
                "/__api/v2/job/**",
                "/__api/v2/miscellaneous/**",
                "/__api/v2/pkg/**",
                "/__api/v2/pkg-job/**",
                "/__api/v2/repository/**",
                "/__api/v2/user/**",
                "/__api/v2/user-rating/**",
                "/__api/v2/user-rating-job/**",

                "/__feed/**",
                "/__secured/jobdata/*/download",
                "/__multipage/**",
                "/__passwordreset/*",
                "/__pkg/all*.json.gz",
                "/__pkgdownload/**",
                "/__pkgicon/all.tar.gz",
                "/__genericpkgicon.png",
                "/__pkgicon/**",
                "/__pkgscreenshot/**",
                "/__pkgsearch/**",
                "/opensearch.xml",
                "/__reference/all*.json.gz",
                "/__repository/all*.json.gz",
                "/__repository/*/repositorysource/*/import",
                "/__repository/*/import")
                .forEach(p -> registry.addInterceptor(new MetricsInterceptor(p))
                        .addPathPatterns(p));
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
