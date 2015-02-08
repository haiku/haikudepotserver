/*
* Copyright 2015, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.pkg;

public class PkgLocalization {

    public String naturalLanguageCode;

    public String title;

    public PkgLocalization() {
    }

    public PkgLocalization(String naturalLanguageCode, String title) {
        this.naturalLanguageCode = naturalLanguageCode;
        this.title = title;
    }
}
