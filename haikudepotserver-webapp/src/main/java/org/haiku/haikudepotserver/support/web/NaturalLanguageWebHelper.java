/*
 * Copyright 2014-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.base.Strings;
import jakarta.servlet.http.HttpServletRequest;
import org.haiku.haikudepotserver.reference.model.MalformedNaturalLanguageCodeException;
import org.haiku.haikudepotserver.reference.model.NaturalLanguageCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * <p>Helper (static) methods for the multipage part of the system.</p>
 */

public class NaturalLanguageWebHelper {

    protected static final Logger LOGGER = LoggerFactory.getLogger(NaturalLanguageWebHelper.class);

    /**
     * <p>This will look at parameters on the supplied request and will return a natural language.  It will
     * resort to English language if no other language is able to be derived.</p>
     */

    public static NaturalLanguageCoordinates deriveNaturalLanguageCoordinates(HttpServletRequest request) {
        if (null != request) {
            String naturalLanguageCode = request.getParameter(WebConstants.KEY_NATURALLANGUAGECODE);

            if (!Strings.isNullOrEmpty(naturalLanguageCode)) {
                try {
                    return NaturalLanguageCoordinates.fromCode(naturalLanguageCode);
                }
                catch (MalformedNaturalLanguageCodeException ignore) {
                    LOGGER.info("the natural language '{}' was specified, but was not able to be found", naturalLanguageCode);
                }
            }

            // see if we can deduce it from the locale.

            Locale locale = request.getLocale();

            if (null != locale) {
                try {
                    return NaturalLanguageCoordinates.fromLocale(locale);
                }
                catch (MalformedNaturalLanguageCodeException ignore) {
                    LOGGER.info("the natural language '{}' was specified, but was not able to be found", naturalLanguageCode);
                }
            }
        }

        return NaturalLanguageCoordinates.english();
    }

}
