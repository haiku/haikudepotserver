/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.naturallanguage.model;

import org.haiku.haikudepotserver.reference.model.NaturalLanguageCoordinates;

import java.util.Properties;
import java.util.Set;

/**
 * <p>This service is designed to help with more complex queries around natural languages.</p>
 *
 * <p>It is also designed to help with queries related to lookup of localization messages.</p>
 */

public interface NaturalLanguageService {

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
