/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.servlets.HealthCheckServlet;
import com.codahale.metrics.servlets.MetricsServlet;
import com.codahale.metrics.servlets.PingServlet;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.MultipleInvocationListener;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImplExporter;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.support.ErrorResolverImpl;
import org.haiku.haikudepotserver.metrics.MetricsFilter;
import org.haiku.haikudepotserver.metrics.MetricsInvocationListener;
import org.haiku.haikudepotserver.singlepage.SinglePageTemplateFrequencyMetrics;
import org.haiku.haikudepotserver.singlepage.SinglePageTemplateFrequencyMetricsFilter;
import org.haiku.haikudepotserver.support.desktopapplication.DesktopApplicationMinimumVersionFilter;
import org.haiku.haikudepotserver.support.jsonrpc4j.ErrorLoggingInvocationListener;
import org.haiku.haikudepotserver.support.jsonrpc4j.HttpStatusCodeProvider;
import org.haiku.haikudepotserver.support.logging.LoggingFilter;
import org.haiku.haikudepotserver.support.web.DelayFilter;
import org.haiku.haikudepotserver.support.web.ErrorServlet;
import org.haiku.haikudepotserver.support.web.RemoteLogCaptureServlet;
import org.haiku.haikudepotserver.support.web.SessionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.filter.ForwardedHeaderFilter;

import javax.servlet.Filter;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpSessionListener;

@Configuration
@Import(WebConfig.class)
public class ServletConfig {

    @Bean
    public HttpSessionListener httpSessionListener() {
        return new SessionListener();
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> errorServlet() {
        ServletRegistrationBean<HttpServlet> servletRegistration = new ServletRegistrationBean<>();
        servletRegistration.setServlet(new ErrorServlet());
        servletRegistration.setLoadOnStartup(1);
        servletRegistration.addUrlMappings("/__error");
        servletRegistration.setAsyncSupported(true);
        servletRegistration.setName("error-servlet");
        return servletRegistration;
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> remoteLogCaptureServlet() {
        ServletRegistrationBean<HttpServlet> servletRegistration = new ServletRegistrationBean<>();
        servletRegistration.setServlet(new RemoteLogCaptureServlet());
        servletRegistration.setLoadOnStartup(1);
        servletRegistration.addUrlMappings("/__log/capture");
        servletRegistration.setAsyncSupported(true);
        servletRegistration.setName("remote-log-capture");
        return servletRegistration;
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> metricAdminHealthCheckServlet() {
        ServletRegistrationBean<HttpServlet> servletRegistration = new ServletRegistrationBean<>();
        servletRegistration.setServlet(new HealthCheckServlet());
        servletRegistration.setLoadOnStartup(1);
        servletRegistration.addUrlMappings("/__metric/healthcheck");
        servletRegistration.setAsyncSupported(true);
        servletRegistration.setName("metric-admin-health-check");
        return servletRegistration;
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> metricAdminMetricsServlet() {
        ServletRegistrationBean<HttpServlet> servletRegistration = new ServletRegistrationBean<>();
        servletRegistration.setServlet(new MetricsServlet());
        servletRegistration.setLoadOnStartup(1);
        servletRegistration.addUrlMappings("/__metric/metrics");
        servletRegistration.setAsyncSupported(true);
        servletRegistration.setName("metric-admin-metrics");
        return servletRegistration;
    }

    @Bean
    public ServletRegistrationBean<HttpServlet> metricAdminPingServlet() {
        ServletRegistrationBean<HttpServlet> servletRegistration = new ServletRegistrationBean<>();
        servletRegistration.setServlet(new PingServlet());
        servletRegistration.setLoadOnStartup(1);
        servletRegistration.addUrlMappings("/__metric/ping");
        servletRegistration.setAsyncSupported(true);
        servletRegistration.setName("metric-admin-ping");
        return servletRegistration;
    }

    @Bean
    public FilterRegistrationBean<Filter> forwardedHeaderFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ForwardedHeaderFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(5);
        registrationBean.setName("forwarded-header-filter");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<Filter> desktopApplicationMinimumVersionFilter(
            @Value("${desktop.application.version.min:}") String minimumVersionString) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new DesktopApplicationMinimumVersionFilter(minimumVersionString));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(10);
        registrationBean.setName("desktop-application-minimum-version-filter");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<Filter> delayFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new DelayFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(15);
        registrationBean.setName("delay-filter");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<Filter> metricsFilter(MetricRegistry metricRegistry) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new MetricsFilter(metricRegistry));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(20);
        registrationBean.setName("metrics-filter");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<Filter> loggingFilter(ServerRuntime serverRuntime) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new LoggingFilter(serverRuntime));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(25);
        registrationBean.setName("logging-filter");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<Filter> singlePageTemplateFrequencyMetricsFilter(
            SinglePageTemplateFrequencyMetrics metrics) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new SinglePageTemplateFrequencyMetricsFilter(metrics));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(30);
        registrationBean.setName("single-page-template-frequency-metrics-filter");
        return registrationBean;
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
