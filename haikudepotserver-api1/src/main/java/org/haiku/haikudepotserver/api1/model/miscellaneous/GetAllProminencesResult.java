/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.miscellaneous;

import java.util.List;

public class GetAllProminencesResult {

    public List<Prominence> prominences;

    public GetAllProminencesResult() {
    }

    public GetAllProminencesResult(List<Prominence> prominences) {
        this.prominences = prominences;
    }

    public static class Prominence {

        public Integer ordering;
        public String name;

        public Prominence() {
        }

        public Prominence(Integer ordering, String name) {
            this.ordering = ordering;
            this.name = name;
        }
    }

}
