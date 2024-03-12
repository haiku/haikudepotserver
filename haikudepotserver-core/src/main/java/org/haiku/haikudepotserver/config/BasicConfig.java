/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Charsets;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.captcha.CaptchaServiceImpl;
import org.haiku.haikudepotserver.captcha.DatabaseCaptchaRepository;
import org.haiku.haikudepotserver.captcha.SimpleMathProblemCaptchaAlgorithm;
import org.haiku.haikudepotserver.captcha.model.CaptchaRepository;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationService;
import org.haiku.haikudepotserver.graphics.bitmap.PngOptimizationServiceFactory;
import org.haiku.haikudepotserver.graphics.hvif.HvifRenderingService;
import org.haiku.haikudepotserver.graphics.hvif.HvifRenderingServiceFactory;
import org.haiku.haikudepotserver.security.PasswordEncoder;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.freemarker.LocalizedTemplateLoader;
import org.haiku.haikudepotserver.support.logging.LoggingSetupOrchestration;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.security.crypto.keygen.KeyGenerators;
import org.springframework.stereotype.Controller;

import java.util.List;

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
    public PngOptimizationService pngOptimizationService(
            @Value("${hds.optipng.path:}") String optiPngPath) {
        return new PngOptimizationServiceFactory(optiPngPath).getObject();
    }

    @Bean
    public HvifRenderingService hvifRenderingService(
            @Value("${hds.hvif2png.path:}") String hvif2pngPath) throws Exception {
        return new HvifRenderingServiceFactory(hvif2pngPath).getObject();
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
        freemarker.template.Configuration configuration = new freemarker.template.Configuration();
        configuration.setDefaultEncoding(Charsets.UTF_8.name());
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
        messageSource.setDefaultEncoding(Charsets.UTF_8.name());
        messageSource.setBasenames(basenames.toArray(new String[0]));
        return messageSource;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new PasswordEncoder(KeyGenerators.secureRandom(8));
    }

}
