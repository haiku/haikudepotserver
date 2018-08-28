/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GetAllCountriesResult {

    public List<Country> countries;

    public GetAllCountriesResult() {
    }

    public GetAllCountriesResult(List<Country> countries) {
        this.countries = countries;
    }

    public static class Country {

        public String code;
        public String name;

        public Country() {
        }

        public Country(String code, String name) {
            this.code = code;
            this.name = name;
        }

    }

}
