/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import org.haiku.haikudepotserver.support.web.ErrorServlet;
import org.haiku.haikudepotserver.support.web.RemoteLogCaptureServlet;
import org.haiku.haikudepotserver.support.web.SessionListener;
import org.springframework.web.WebApplicationInitializer;
import org.springframework.web.context.ContextLoaderListener;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.filter.DelegatingFilterProxy;
import org.springframework.web.servlet.DispatcherServlet;

import javax.servlet.FilterRegistration;
import javax.servlet.ServletContext;
import javax.servlet.ServletRegistration;

/**
 * <p>This is a spring-construct that is auto-discovered in order to bootstrap the
 * servlet environment.  This (mostly) replaces the <code>web.xml</code> file.</p>
 */

public class WebInitializer implements WebApplicationInitializer {

    @Override
    public void onStartup(ServletContext servletContext) {

        AnnotationConfigWebApplicationContext rootContext = new AnnotationConfigWebApplicationContext();
        rootContext.register(AppConfig.class);

        servletContext.addListener(new ContextLoaderListener(rootContext));
        servletContext.addListener(new SessionListener());

        registerErrorServlet(servletContext);
        registerRemoteLogCaptureServlet(servletContext);
        registerSpringDispatcherServlet(servletContext);

        // note that the spring security filters are not included here.

        registerSpringFilter(servletContext, "delayFilter", "/*");
        registerSpringFilter(servletContext, "forwardedHeaderFilter", "/*");
        registerSpringFilter(servletContext, "metricsFilter", "/*");
        registerSpringFilter(servletContext, "springSecurityFilterChain", "/*");
        registerSpringFilter(servletContext, "loggingFilter", "/*");
        registerSpringFilter(servletContext, "singlePageTemplateFrequencyMetricsFilter", "/__js/app/*");
        registerSpringFilter(servletContext, "desktopApplicationMetricsFilter", "/*");
        registerSpringFilter(servletContext, "desktopApplicationMinimumVersionFilter", "/*");

        // would be nice to add the error handler here, but this not possible in this
        // mechanism right now evidently.

    }

    private void registerSpringFilter(
            ServletContext servletContext,
            String beanName,
            String... urlPatterns) {
        FilterRegistration.Dynamic dynamic = servletContext.addFilter(beanName, new DelegatingFilterProxy(beanName));
        dynamic.addMappingForUrlPatterns(null, false, urlPatterns);
        dynamic.setAsyncSupported(true);
    }

    private void registerErrorServlet(ServletContext servletContext) {
        ServletRegistration.Dynamic dispatcher = servletContext.addServlet("error-servlet", ErrorServlet.class);
        dispatcher.setLoadOnStartup(1);
        dispatcher.addMapping("/__error");
        dispatcher.setAsyncSupported(true);
    }

    private void registerRemoteLogCaptureServlet(ServletContext servletContext) {
        ServletRegistration.Dynamic dispatcher = servletContext.addServlet(
                "remote-log-capture", RemoteLogCaptureServlet.class);
        dispatcher.setLoadOnStartup(1);
        dispatcher.addMapping("/__log/capture");
        dispatcher.setAsyncSupported(true);
    }

    private void registerSpringDispatcherServlet(ServletContext servletContext) {
        AnnotationConfigWebApplicationContext dispatcherContext = new AnnotationConfigWebApplicationContext();
        dispatcherContext.register(ServletConfig.class);

        ServletRegistration.Dynamic dispatcher = servletContext.addServlet(
                "dispatcher",
                new DispatcherServlet(dispatcherContext));
        dispatcher.setLoadOnStartup(1);
        dispatcher.addMapping("/");
        dispatcher.setAsyncSupported(true);
    }

}
