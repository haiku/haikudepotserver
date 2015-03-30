/*
* Copyright 2015, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.pkg;

public class PkgLocalization {

    public String naturalLanguageCode;

    public String title;

    public String summary;

    public String description;

    public PkgLocalization() {
    }

    public PkgLocalization(
            String naturalLanguageCode,
            String title,
            String summary,
            String description) {
        this.naturalLanguageCode = naturalLanguageCode;
        this.title = title;
        this.description = description;
        this.summary = summary;
    }
}
