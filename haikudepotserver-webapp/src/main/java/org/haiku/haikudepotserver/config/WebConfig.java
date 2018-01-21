/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableSet;
import com.googlecode.jsonrpc4j.MultipleInvocationListener;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImplExporter;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.support.ErrorResolverImpl;
import org.haiku.haikudepotserver.multipage.MultipageLocaleResolver;
import org.haiku.haikudepotserver.support.jsonrpc4j.ErrorLoggingInvocationListener;
import org.haiku.haikudepotserver.support.jsonrpc4j.HttpStatusCodeProvider;
import org.haiku.haikudepotserver.metrics.MetricsInvocationListener;
import org.haiku.haikudepotserver.metrics.MetricsInterceptor;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;

@EnableWebMvc
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
        registry.addResourceHandler("/__js/**").addResourceLocations("/js/").setCachePeriod(0);
        registry.addResourceHandler("/__webjars/**").addResourceLocations("/webjars/");
        registry.addResourceHandler("/__css/**").addResourceLocations("/css/");
        registry.addResourceHandler("/__img/**").addResourceLocations("/img/");
        registry.addResourceHandler("/__docs/**").addResourceLocations("/docs/");
        registry.addResourceHandler("favicon.ico").addResourceLocations("/img/favicon.ico");
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry
                .addInterceptor(new MetricsInterceptor(
                        ImmutableSet.of(
                                "__maintenance",
                                "__pkgdownload",
                                "__pkgsearch",
                                "__pkgicon",
                                "__genericpkgicon",
                                "__pkgscreenshot",
                                "__repository",
                                "__pkg"
                        )
                ))
                .addPathPatterns(
                        "/__**",
                        "/__**/*"
                );
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

}
