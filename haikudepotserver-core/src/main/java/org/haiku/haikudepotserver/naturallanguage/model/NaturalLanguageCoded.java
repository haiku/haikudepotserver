/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.naturallanguage.model;

import org.haiku.haikudepotserver.naturallanguage.NaturalLanguageCodeComparator;

import java.util.Comparator;

/**
 * <p>Any concrete class that is able to provide the key data points to identify a language should
 * implement this interface.</p>
 */

public interface NaturalLanguageCoded {

    Comparator<NaturalLanguageCoded> NATURAL_LANGUAGE_CODE_COMPARATOR = new NaturalLanguageCodeComparator();

    String getLanguageCode();

    String getScriptCode();

    String getCountryCode();

}
