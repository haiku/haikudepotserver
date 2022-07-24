/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.MultipleInvocationListener;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImplExporter;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.support.ErrorResolverImpl;
import org.haiku.haikudepotserver.api1.support.ObjectMapperFactory;
import org.haiku.haikudepotserver.metrics.MetricsInterceptor;
import org.haiku.haikudepotserver.metrics.MetricsInvocationListener;
import org.haiku.haikudepotserver.multipage.MultipageLocaleResolver;
import org.haiku.haikudepotserver.support.jsonrpc4j.ErrorLoggingInvocationListener;
import org.haiku.haikudepotserver.support.jsonrpc4j.HttpStatusCodeProvider;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.stereotype.Controller;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;

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
        registry.addResourceHandler(WebConstants.ANT_PATTERN_JS).addResourceLocations("/js/").setCachePeriod(0);
        registry.addResourceHandler(WebConstants.ANT_PATTERN_WEBJAR).addResourceLocations("/webjars/");
        registry.addResourceHandler(WebConstants.ANT_PATTERN_CSS).addResourceLocations("/css/");
        registry.addResourceHandler(WebConstants.ANT_PATTERN_IMG).addResourceLocations("/img/");
        registry.addResourceHandler(WebConstants.ANT_PATTERN_DOCS).addResourceLocations("/docs/");
        registry.addResourceHandler("favicon.ico").addResourceLocations("/img/favicon.ico");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {

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
    public void configureViewResolvers(ViewResolverRegistry registry) {
        registry.jsp("/WEB-INF/views/", ".jsp");
    }

    @Bean("localeResolver")
    public LocaleResolver localeResolver(ServerRuntime serverRuntime) {
        return new MultipageLocaleResolver(serverRuntime);
    }

    @Bean
    public BeanFactoryPostProcessor autoJsonRpcServiceImplExporter(
            ObjectMapper objectMapper,
            MetricRegistry metricRegistry) {

        MetricsInvocationListener metricsInvocationListener = new MetricsInvocationListener(metricRegistry);

        AutoJsonRpcServiceImplExporter exporter = new AutoJsonRpcServiceImplExporter();

        // don't log exceptions because they will be logged in the invocation listener
        exporter.setShouldLogInvocationErrors(false);

        // set the content type explicitly because otherwise it is application/json-rpc
        exporter.setContentType("application/json");

        // prevents spring from also logging the exception
        exporter.setRegisterTraceInterceptor(false);

        // allows hds control over how the exception is logged
        exporter.setInvocationListener(
                new MultipleInvocationListener(
                        new ErrorLoggingInvocationListener(),
                        metricsInvocationListener
                )
        );

        exporter.setHttpStatusCodeProvider(new HttpStatusCodeProvider());

        exporter.setErrorResolver(new ErrorResolverImpl());
        exporter.setObjectMapper(objectMapper);

        return exporter;
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
