/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GetAllNaturalLanguagesResult {

    public List<NaturalLanguage> naturalLanguages;

    public GetAllNaturalLanguagesResult() {
    }

    public GetAllNaturalLanguagesResult(List<NaturalLanguage> naturalLanguages) {
        this.naturalLanguages = naturalLanguages;
    }

    public static class NaturalLanguage {

        public String code;
        public String name;

        /**
         * <P>This indicates that the language is one of the world's popular languages that are
         * spoken by a very large number of people.</P>
         */

        public Boolean isPopular;

        /**
         * <p>This indicates that the language has data associated with it; user ratings or
         * package version localization data.</p>
         */

        public Boolean hasData;

        /**
         * <p>This boolean indicates that the language has localization messages (primarily for the
         * user interface) recorded against it.</p>
         */

        public Boolean hasLocalizationMessages;

        public NaturalLanguage() {
        }

        public NaturalLanguage(
                String code,
                String name,
                Boolean isPopular,
                Boolean hasData,
                Boolean hasLocalizationMessages) {
            this.code = code;
            this.name = name;
            this.isPopular = isPopular;
            this.hasData = hasData;
            this.hasLocalizationMessages = hasLocalizationMessages;
        }

    }

}
