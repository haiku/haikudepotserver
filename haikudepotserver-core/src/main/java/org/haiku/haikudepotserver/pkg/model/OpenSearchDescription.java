/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;

/**
 * <p>This model object is used to describe the data required to fill the freemarker template
 * that renders out an OpenSearch definition.  See
 * &quot;PkgSearchController&quot; for usage.</p>
 */

public class OpenSearchDescription {

    private String description;
    private String baseUrl;
    private NaturalLanguageCoordinates naturalLanguageCoordinates;
    private String shortName;

    public String getShortName() {
        return shortName;
    }

    public void setShortName(String shortName) {
        this.shortName = shortName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public NaturalLanguageCoordinates getNaturalLanguageCoordinates() {
        return naturalLanguageCoordinates;
    }

    public void setNaturalLanguageCoordinates(NaturalLanguageCoordinates naturalLanguageCoordinates) {
        this.naturalLanguageCoordinates = naturalLanguageCoordinates;
    }
}
