/*
 * Copyright 2016-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.naturallanguage.model;

import org.haiku.haikudepotserver.naturallanguage.NaturalLanguageCodeComparator;

import java.util.*;

/**
 * <p>This service is designed to help with more complex queries around natural languages.</p>
 *
 * <p>It is also designed to help with queries related to lookup of localization messages.</p>
 */

public interface NaturalLanguageService {

    Comparator<NaturalLanguageCoded> NATURAL_LANGUAGE_CODE_COMPARATOR = new NaturalLanguageCodeComparator();

    /**
     * <p>Tries as best possible to match the supplied language with the optional presented in the <code>coded</code>
     * parameter.</p>
     *
     * <p>Note that this method will fail if the <code>codeSortedNaturalLanguages</code> are not sorted with the
     * {@link Comparator} found at {@link NaturalLanguageCoded#NATURAL_LANGUAGE_CODE_COMPARATOR}.</p>
     */
    <T extends NaturalLanguageCoded> Optional<T> tryGetBestMatchFromList(
            List<T> codeSortedNaturalLanguages,
            NaturalLanguageCoded targetCoded);

    List<? extends NaturalLanguageCoded> getAllSupportedCoordinates();

    /**
     * <p>Returns a set of all natural languages that have localizations for the HDS user
     * interface.</p>
     */
    Set<NaturalLanguageCoordinates> findNaturalLanguagesWithLocalizationMessages();

    /**
     * <p>Returns a set of all natural languages that have data in the HDS system for
     * user ratings etc...</p>
     */
    Set<NaturalLanguageCoordinates> findNaturalLanguagesWithData();

    Properties getAllLocalizationMessages(NaturalLanguageCoordinates naturalLanguageCoordinates);

}
