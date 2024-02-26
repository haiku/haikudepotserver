/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.reference.model;

import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.regex.Pattern;

/**
 * <p>This object carries the necessary details necessary to identify a language.</p>
 */

public record NaturalLanguageCoordinates(String languageCode, String countryCode, String scriptCode) {

    public final static Pattern PATTERN_LANGUAGE_CODE = Pattern.compile("^[a-z]{2,3}$");
    public final static Pattern PATTERN_COUNTRY_CODE = Pattern.compile("^[A-Z]{2}$");
    public final static Pattern PATTERN_SCRIPT_CODE = Pattern.compile("^[A-Z][a-z]{3}$");

    public static NaturalLanguageCoordinates fromCode(String code) {
        List<String> parts = Splitter.on('_').splitToList(code);

        if (parts.isEmpty()) {
            throw new MalformedNaturalLanguageCodeException("missing any parts in the code [" + code + "]");
        }

        return new NaturalLanguageCoordinates(
                parts.get(0),
                parts.size() > 1 ? parts.get(1) : null,
                parts.size() > 2 ? parts.get(2) : null);
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

        if (null != countryCode && !PATTERN_SCRIPT_CODE.matcher(scriptCode).matches()) {
            throw new MalformedNaturalLanguageCodeException("bad script code [" + scriptCode + "]");
        }

    }

    public String getCode() {
        StringBuilder result = new StringBuilder(languageCode);
        boolean hasCountryCode = StringUtils.isNotBlank(countryCode);
        boolean hasScriptCode = StringUtils.isNotBlank(scriptCode);

        if (hasScriptCode || hasCountryCode) {
            result.append('_');
        }

        if (hasCountryCode) {
            result.append(countryCode);
        }

        if (hasScriptCode) {
            result.append(scriptCode);
        }

        return result.toString();
    }

    @Override
    public String toString() {
        return getCode();
    }

}
