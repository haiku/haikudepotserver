/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.internationalization;

import org.haiku.haikudepotserver.multipage.PassiveContentRepository;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * <p>Typically the only thing that a controller class requires to create an instance of
 * {@link InternationalizationSupplier} is the {@link Locale} however there are other
 * service classes that are also required. This factory class is able to make those
 * instances and only requires the {@link Locale} to be supplied.</p>
 */

@Component
public class InternationalizationSupplierFactory {

    private final MessageSource messageSource;
    private final PassiveContentRepository passiveContentRepository;

    public InternationalizationSupplierFactory(MessageSource messageSource, PassiveContentRepository passiveContentRepository) {
        this.messageSource = messageSource;
        this.passiveContentRepository = passiveContentRepository;
    }

    public InternationalizationSupplier create(Locale locale) {
        return new InternationalizationSupplier(messageSource, passiveContentRepository, locale);
    }

}
