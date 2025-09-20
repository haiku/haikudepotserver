/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import freemarker.template.Configuration;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.captcha.CaptchaServiceImpl;
import org.haiku.haikudepotserver.captcha.DatabaseCaptchaRepository;
import org.haiku.haikudepotserver.captcha.SimpleMathProblemCaptchaAlgorithm;
import org.haiku.haikudepotserver.captcha.model.CaptchaRepository;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationServiceFactory;
import org.haiku.haikudepotserver.graphics.bitmap.PngThumbnailService;
import org.haiku.haikudepotserver.graphics.bitmap.PngThumbnailServiceFactory;
import org.haiku.haikudepotserver.graphics.hvif.HvifRenderingService;
import org.haiku.haikudepotserver.graphics.hvif.HvifRenderingServiceFactory;
import org.haiku.haikudepotserver.security.PasswordEncoder;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveEventConsumer;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveEventNotifyControl;
import org.haiku.haikudepotserver.support.eventing.InterProcessEventPgConfig;
import org.haiku.haikudepotserver.support.eventing.InterProcessEventPgListenService;
import org.haiku.haikudepotserver.support.eventing.InterProcessEventPgNotifyService;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessApplicationEvent;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessEvent;
import org.haiku.haikudepotserver.support.freemarker.LocalizedTemplateLoader;
import org.haiku.haikudepotserver.support.logging.LoggingSetupOrchestration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Controller;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@ComponentScan(
        basePackages = "org.haiku.haikudepotserver",
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ANNOTATION, value = Controller.class),
        }
)
@Import({PersistenceConfig.class})
public class BasicConfig {

    // -------------------------------------
    // SUNDRY

    @Bean
    public LoggingSetupOrchestration loggingSetupOrchestration() {
        return new LoggingSetupOrchestration();
    }

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapperFactory().getObject();
    }

    @Bean
    public PngThumbnailService pngThumbnailService(
            @Value("${hds.graphics-server.base-uri:}") String graphicsServerBaseUri) {
        return new PngThumbnailServiceFactory(graphicsServerBaseUri).getObject();
    }

    @Bean
    public PngOptimizationService pngOptimizationService(
            @Value("${hds.graphics-server.base-uri:}") String graphicsServerBaseUri) {
        return new PngOptimizationServiceFactory(
                graphicsServerBaseUri).getObject();
    }

    @Bean
    public HvifRenderingService hvifRenderingService(
            @Value("${hds.graphics-server.base-uri:}") String graphicsServerBaseUri) throws Exception {
        return new HvifRenderingServiceFactory(graphicsServerBaseUri).getObject();
    }

    @Bean
    public RuntimeInformationService runtimeInformationService() {
        return new RuntimeInformationService();
    }

    // -------------------------------------
    // CAPTCHA SUPPORT

    // This bean should only need to be directly accessed for the purpose of
    // integration testing.

    @Bean
    public CaptchaRepository captchaRepository(
            ServerRuntime serverRuntime,
            @Value("${hds.captcha.expiry-seconds:120}") Long expirySeconds
    ) {
        return new DatabaseCaptchaRepository(serverRuntime, expirySeconds);
    }

    @Bean
    public CaptchaService captchaService(CaptchaRepository captchaRepository) {
        return new CaptchaServiceImpl(
                new SimpleMathProblemCaptchaAlgorithm(),
                captchaRepository);
    }

    // -------------------------------------
    // FREEMARKER

    @Bean
    public freemarker.template.Configuration opensearchFreemarkerConfiguration(ResourceLoader resourceLoader) {
        return createFreemarkerConfiguration(resourceLoader, "classpath:/opensearch/");
    }

    @Bean
    public freemarker.template.Configuration emailFreemarkerConfiguration(ResourceLoader resourceLoader) {
        return createFreemarkerConfiguration(resourceLoader, "classpath:/mail/");
    }

    private freemarker.template.Configuration createFreemarkerConfiguration(
            ResourceLoader resourceLoader,
            String templateBase) {
        freemarker.template.Configuration configuration = new freemarker.template.Configuration(
                Configuration.getVersion());
        configuration.setDefaultEncoding(StandardCharsets.UTF_8.name());
        configuration.setLocalizedLookup(false);
        configuration.setTemplateLoader(new LocalizedTemplateLoader(resourceLoader, templateBase));
        return configuration;
    }

    // -------------------------------------
    // LOCALIZATION

    @Bean
    public MessageSource messageSource(
            @Qualifier("messageSourceBaseNames") List<String> basenames) {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setDefaultEncoding(StandardCharsets.UTF_8.name());
        messageSource.setBasenames(basenames.toArray(new String[0]));
        return messageSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder(KeyGenerators.secureRandom(8));
    }

    // -------------------------------------
    // INTER-PROCESS EVENTING

    @Bean
    public QueryCacheRemoveEventNotifyControl queryCacheRemoveEventNotifyControl() {
        return new QueryCacheRemoveEventNotifyControl();
    }

    @Bean
    public InterProcessEventPgListenService interProcessEventPgListenService(
            ObjectMapper objectMapper,
            DataSource dataSource,
            InterProcessEventPgConfig config,
            ApplicationEventPublisher applicationEventPublisher,
            ServerRuntime serverRuntime,
            QueryCacheRemoveEventNotifyControl notifyControl
    ) {
        QueryCacheRemoveEventConsumer queryCacheRemoveEventConsumer = new QueryCacheRemoveEventConsumer(serverRuntime, notifyControl);
        Consumer<InterProcessEvent>  applicationEventConsumer =  (event) -> {
            if (event instanceof InterProcessApplicationEvent interProcessApplicationEvent) {
                applicationEventPublisher.publishEvent(interProcessApplicationEvent);
            }
        };

        return new InterProcessEventPgListenService(
                objectMapper, dataSource, config,
                applicationEventConsumer.andThen(queryCacheRemoveEventConsumer)
        );
    }

    @Bean
    public InterProcessEventPgNotifyService interProcessEventPgNotifyService(
            ObjectMapper objectMapper,
            DataSource dataSource,
            InterProcessEventPgConfig config) {
        return new InterProcessEventPgNotifyService(objectMapper, dataSource, config);
    }

}
