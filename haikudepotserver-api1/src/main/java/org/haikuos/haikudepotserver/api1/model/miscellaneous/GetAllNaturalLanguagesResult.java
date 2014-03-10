/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.miscellaneous;

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

        public NaturalLanguage() {
        }

        public NaturalLanguage(String code, String name) {
            this.code = code;
            this.name = name;
        }

    }

}
