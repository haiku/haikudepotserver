/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.naturallanguage.model;

import java.util.Properties;

/**
 * <p>This service is designed to help with more complex queries around natural languages.</p>
 *
 * <p>It is also designed to help with queries related to lookup of localization messages.</p>
 */

public interface NaturalLanguageService {


    /**
     * <p>Returns true if the natural language provided has stored messages.</p>
     */

    boolean hasLocalizationMessages(String naturalLanguageCode);

    /**
     * <p>Returns true if there is user data stored in the database for this language.</p>
     */

    boolean hasData(String naturalLanguageCode);

    Properties getAllLocalizationMessages(String naturalLanguageCode);

}
