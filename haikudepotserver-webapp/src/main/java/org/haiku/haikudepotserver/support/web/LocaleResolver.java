/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class LocaleResolver implements org.springframework.web.servlet.LocaleResolver {

    protected static final Logger LOGGER = LoggerFactory.getLogger(LocaleResolver.class);

    private static final String KEY_LOCALE = "hds.locale";

    private final List<NaturalLanguageCoordinates> codeSortedPossibleCoordinates;

    private final NaturalLanguageService naturalLanguageService;

    public LocaleResolver(NaturalLanguageService naturalLanguageService) {
        this.naturalLanguageService = naturalLanguageService;
        this.codeSortedPossibleCoordinates = naturalLanguageService
                .findNaturalLanguagesWithLocalizationMessages()
                .stream()
                .map(NaturalLanguageCoordinates::fromCoded)
                .sorted(NaturalLanguageCoded.NATURAL_LANGUAGE_CODE_COMPARATOR)
                .toList();
    }

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        Locale locale = (Locale) request.getAttribute(KEY_LOCALE);

        if (null == locale) {
            locale = deriveCoordinatesFromRequest(request).toLocale();
            request.setAttribute(KEY_LOCALE, locale);
        }

        return locale;
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        if (codeSortedPossibleCoordinates.contains(NaturalLanguageCoordinates.fromLocale(locale))) {
            request.setAttribute(KEY_LOCALE, locale);
        }
    }

    private NaturalLanguageCoordinates deriveCoordinatesFromRequest(HttpServletRequest request) {
        return tryDeriveCoordinatesFromRequest(request).orElseGet(NaturalLanguageCoordinates::english);
    }

    private Optional<NaturalLanguageCoordinates> tryDeriveCoordinatesFromRequest(HttpServletRequest request) {

        // If the request carries a <code>..?locale=en</code> style header then observe this in preference
        // to any headers etc...

        String queryParameter = request.getParameter(WebConstants.KEY_NATURALLANGUAGECODE);

        if (StringUtils.isNotBlank(queryParameter)) {
            return naturalLanguageService.tryGetBestMatchFromList(
                    codeSortedPossibleCoordinates,
                    NaturalLanguageCoordinates.fromCode(queryParameter));
        }

        // This will prevent the default value from being used. The assumption here is that the underlying
        // source of the locales is this header.

        if (StringUtils.isBlank(request.getHeader(HttpHeaders.ACCEPT_LANGUAGE))) {
            return Optional.empty();
        }

        List<Locale> headerLocales = Collections.list(request.getLocales());

        for (Locale headerLocale : headerLocales) {
            Optional<NaturalLanguageCoordinates> result = naturalLanguageService.tryGetBestMatchFromList(
                    codeSortedPossibleCoordinates,
                    NaturalLanguageCoordinates.fromLocale(headerLocale));

            if (result.isPresent()) {
                return result;
            }
        }

        return Optional.empty();
    }

}
