/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.health.HealthCheckRegistry;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.LocalJobServiceImpl;
import org.haiku.haikudepotserver.job.model.JobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationLookupService;
import org.haiku.haikudepotserver.storage.LocalDataStorageServiceImpl;
import org.haiku.haikudepotserver.storage.model.DataStorageService;
import org.haiku.haikudepotserver.support.ClientIdentifierSupplier;
import org.haiku.haikudepotserver.support.HttpRequestClientIdentifierSupplier;
import org.haiku.haikudepotserver.thymeleaf.Dialect;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.PropertySource;
import org.springframework.core.io.Resource;
import org.springframework.mail.MailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.web.context.support.ServletContextAttributeExporter;

import java.util.Collection;
import java.util.List;
import java.util.Properties;

@Import({BasicConfig.class, ScheduleConfig.class})
@PropertySource(
        value = {
                "classpath:application.yml",
                "classpath:local.properties",
                "${config.properties:file-not-found.properties}"
        },
        ignoreResourceNotFound = true
)
@Configuration
public class AppConfig {

    @Bean
    public ClientIdentifierSupplier clientIdentifierSupplier() {
        return new HttpRequestClientIdentifierSupplier();
    }

    @Bean
    public JobService jobService(
            DataStorageService dataStorageService,
            Collection<JobRunner> jobRunners) {
        return new LocalJobServiceImpl(dataStorageService, jobRunners);
    }

    @Bean
    public DataStorageService dataStorageService(
            @Value("${deployment.isproduction:false}") Boolean isProduction) {
        return new LocalDataStorageServiceImpl(isProduction);
    }

    @Bean
    public MailSender mailSender(
            @Value("${smtp.host}") String host,
            @Value("${smtp.port:25}") Integer port,
            @Value("${smtp.auth:false}") Boolean smtpAuth,
            @Value("${smtp.starttls:false}") Boolean startTls,
            @Value("${smtp.username:}") String username,
            @Value("${smtp.password:}") String password) {

        Properties properties = new Properties();
        properties.put("mail.smtp.auth", smtpAuth);
        properties.put("mail.smtp.starttls.enable", startTls);

        JavaMailSenderImpl result = new JavaMailSenderImpl();

        result.setHost(host);
        result.setPort(port);
        result.setProtocol("smtp");
        result.setJavaMailProperties(properties);

        if (StringUtils.isNotBlank(username)) {
            result.setUsername(username);
        }

        if (StringUtils.isNotBlank(password)) {
            result.setPassword(password);
        }

        return result;
    }

    @Bean
    public ServletContextAttributeExporter servletContextAttributeExporter(
            MetricRegistry metricRegistry,
            HealthCheckRegistry healthCheckRegistry) {
        ServletContextAttributeExporter exporter = new ServletContextAttributeExporter();
        exporter.setAttributes(ImmutableMap.of(
                "com.codahale.metrics.servlets.MetricsServlet.registry", metricRegistry,
                "com.codahale.metrics.servlets.HealthCheckServlet.registry", healthCheckRegistry
        ));
        return exporter;
    }

    @Bean("messageSourceBaseNames")
    public List<String> messageSourceBaseNames() {
        return ImmutableList.of(
                "classpath:messages",
                "classpath:webmessages",
                "classpath:naturallanguagemessages"
        );
    }

    @Bean
    public Dialect processorDialect(
            ServerRuntime serverRuntime,
            PkgLocalizationLookupService pkgLocalizationLookupService,
            @Value("${deployment.isproduction:false}") Boolean isProduction,
            @Value("classpath:/spa1/js/index.txt") Resource singlePageApplicationJavaScriptIndexResource) {
        return new Dialect(
                serverRuntime,
                pkgLocalizationLookupService,
                isProduction,
                singlePageApplicationJavaScriptIndexResource);
    }

}
