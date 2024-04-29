/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.naturallanguage.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.reference.model.MalformedNaturalLanguageCodeException;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>This object carries the necessary details necessary to identify a language.</p>
 *
 * <p>The code is expected to be in the format <code>{LANGUAGE}_{SCRIPT}_{COUNTRY}</code>. The script and the country
 * are optional. Here are some examples;</p>
 *
 * <ul>
 *     <li><code>en</code></li>
 *     <li><code>en_US</code></li>
 *     <li><code>uz_Cyrl_UZ</code></li>
 * </ul>
 *
 * <p>On output the underscore is preferred as a separator and on input either are accepted.</p>
 */

public record NaturalLanguageCoordinates(String languageCode, String scriptCode, String countryCode)
        implements NaturalLanguageCoded, Comparable<NaturalLanguageCoordinates> {

    public final static Pattern PATTERN_LANGUAGE_CODE = Pattern.compile("^[a-z]{2,3}$");

    /**
     * <p>Typically countries are 2-character upper case letters such as <code>DE</code> but can also be three
     * digit characters such as <code>419</code>.</p>
     */
    public final static Pattern PATTERN_COUNTRY_CODE = Pattern.compile("^[A-Z0-9]{2,3}$");
    public final static Pattern PATTERN_SCRIPT_CODE = Pattern.compile("^[A-Z][a-z]{3}$");

    public final static String LANGUAGE_CODE_ENGLISH = "en";
    public final static String LANGUAGE_CODE_GERMAN = "de";
    public final static String LANGUAGE_CODE_SPANISH = "es";
    public final static String LANGUAGE_CODE_FRENCH = "fr";

    private final static Pattern PATTERN_COMPONENT_SEPARATOR = Pattern.compile("[_-]");

    private final static Comparator<NaturalLanguageCoordinates> COMPARATOR = Comparator
            .comparing(NaturalLanguageCoordinates::languageCode, StringUtils::compare)
            .thenComparing(NaturalLanguageCoordinates::countryCode, StringUtils::compare)
            .thenComparing(NaturalLanguageCoordinates::scriptCode, StringUtils::compare);

    public static NaturalLanguageCoordinates english() {
        return new NaturalLanguageCoordinates(LANGUAGE_CODE_ENGLISH, null, null);
    }

    public static NaturalLanguageCoordinates fromCode(String code) {
        Preconditions.checkArgument(null != code, "the code must be provided");
        List<String> parts = Splitter
                .on(PATTERN_COMPONENT_SEPARATOR)
                .limit(3) // there can be variants but we don't handle those
                .splitToList(code);

        if (parts.isEmpty()) {
            throw new MalformedNaturalLanguageCodeException("missing any parts in the code [" + code + "]");
        }

        String languageCode = parts.getFirst();
        String scriptCode = null;
        String countryCode = null;

        for (int i = 1; i < parts.size(); i++) {
            String part = parts.get(i);

            if (!part.isEmpty()) {
                if (PATTERN_COUNTRY_CODE.matcher(part).matches()) {
                    countryCode = part;
                } else if (PATTERN_SCRIPT_CODE.matcher(part).matches()) {
                    scriptCode = part;
                } else {
                    throw new MalformedNaturalLanguageCodeException("the part [" + part
                            + "] is neither a country nor a script");
                }
            }
        }

        return new NaturalLanguageCoordinates(languageCode, scriptCode, countryCode);
    }

    public static NaturalLanguageCoordinates fromLocale(Locale locale) {
        Preconditions.checkArgument(null != locale, "the locale was expected");
        return new NaturalLanguageCoordinates(
                locale.getLanguage(),
                locale.getScript(),
                locale.getCountry()
        );
    }

    public static NaturalLanguageCoordinates fromCoded(NaturalLanguageCoded coded) {
        Preconditions.checkArgument(null != coded, "the coded was expected");

        if (coded instanceof NaturalLanguageCoordinates nlc) {
            return nlc;
        }

        return new NaturalLanguageCoordinates(
                coded.getLanguageCode(),
                coded.getScriptCode(),
                coded.getCountryCode()
        );
    }

    public NaturalLanguageCoordinates {
        Preconditions.checkArgument(null != languageCode, "language code is required");

        languageCode = StringUtils.trimToNull(languageCode);
        countryCode = StringUtils.trimToNull(countryCode);
        scriptCode = StringUtils.trimToNull(scriptCode);

        if (!PATTERN_LANGUAGE_CODE.matcher(languageCode).matches()) {
            throw new MalformedNaturalLanguageCodeException("bad language code [" + languageCode + "]");
        }

        if (null != countryCode && !PATTERN_COUNTRY_CODE.matcher(countryCode).matches()) {
            throw new MalformedNaturalLanguageCodeException("bad country code [" + countryCode + "]");
        }

        if (null != scriptCode && !PATTERN_SCRIPT_CODE.matcher(scriptCode).matches()) {
            throw new MalformedNaturalLanguageCodeException("bad script code [" + scriptCode + "]");
        }

    }

    public String getCode() {
        return Stream.of(languageCode, scriptCode, countryCode)
                .map(StringUtils::trimToNull)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("-"));
    }

    @Override
    public String getLanguageCode() {
        return languageCode();
    }

    @Override
    public String getScriptCode() {
        return scriptCode();
    }

    @Override
    public String getCountryCode() {
        return countryCode();
    }

    public Locale toLocale() {
        return new Locale.Builder()
                .setLanguage(languageCode())
                .setScript(scriptCode())
                .setRegion(countryCode()) // this is the country.
                .build();
    }

    @Override
    public int compareTo(NaturalLanguageCoordinates o) {
        Preconditions.checkArgument(null != o);
        return COMPARATOR.compare(this, o);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NaturalLanguageCoordinates that = (NaturalLanguageCoordinates) o;
        return Objects.equals(languageCode, that.languageCode) && Objects.equals(scriptCode, that.scriptCode) && Objects.equals(countryCode, that.countryCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(languageCode, scriptCode, countryCode);
    }

    @Override
    public String toString() {
        return getCode();
    }

}
