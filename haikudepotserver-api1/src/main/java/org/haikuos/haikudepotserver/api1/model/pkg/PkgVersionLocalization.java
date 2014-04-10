/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.pkg;

public class PkgVersionLocalization {

    public String naturalLanguageCode;

    public String summary;

    public String description;

    public PkgVersionLocalization() {
    }

    public PkgVersionLocalization(String naturalLanguageCode, String summary, String description) {
        this.naturalLanguageCode = naturalLanguageCode;
        this.summary = summary;
        this.description = description;
    }

}
