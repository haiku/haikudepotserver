/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

/**
 * <p>This model object is used to describe the data required to fill the freemarker template
 * that renders out an OpenSearch definition.  See
 * &quot;PkgSearchController&quot; for usage.</p>
 */

public class OpenSearchDescription {

    private String description;
    private String baseUrl;
    private String naturalLanguageCode;
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

    public String getNaturalLanguageCode() {
        return naturalLanguageCode;
    }

    public void setNaturalLanguageCode(String naturalLanguageCode) {
        this.naturalLanguageCode = naturalLanguageCode;
    }
}
