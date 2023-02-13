/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpSessionListener;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.support.desktopapplication.DesktopApplicationMetricsFilter;
import org.haiku.haikudepotserver.support.desktopapplication.DesktopApplicationMinimumVersionFilter;
import org.haiku.haikudepotserver.support.logging.LoggingFilter;
import org.haiku.haikudepotserver.support.web.DelayFilter;
import org.haiku.haikudepotserver.support.web.ErrorServlet;
import org.haiku.haikudepotserver.support.web.RemoteLogCaptureServlet;
import org.haiku.haikudepotserver.support.web.SessionListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.boot.web.servlet.ServletRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.web.filter.ForwardedHeaderFilter;

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
    public FilterRegistrationBean<Filter> forwardedHeaderFilter() {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new ForwardedHeaderFilter());
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(5);
        registrationBean.setName("forwarded-header-filter");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<Filter> desktopApplicationMetricsFilter(MeterRegistry meterRegistry) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new DesktopApplicationMetricsFilter(meterRegistry));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(5);
        registrationBean.setName("desktop-application-metrics-filter");
        return registrationBean;
    }

    @Bean
    public FilterRegistrationBean<Filter> desktopApplicationMinimumVersionFilter(
            @Value("${hds.desktop.application.version.min:}") String minimumVersionString) {
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
    public FilterRegistrationBean<Filter> loggingFilter(ServerRuntime serverRuntime) {
        FilterRegistrationBean<Filter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(new LoggingFilter(serverRuntime));
        registrationBean.addUrlPatterns("/*");
        registrationBean.setOrder(25);
        registrationBean.setName("logging-filter");
        return registrationBean;
    }

}
