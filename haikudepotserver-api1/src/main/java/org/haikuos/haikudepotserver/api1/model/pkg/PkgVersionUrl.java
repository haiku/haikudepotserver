/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.pkg;

public class PkgVersionUrl {

    public String url;
    public String urlTypeCode;

    public PkgVersionUrl() {
    }

    public PkgVersionUrl(String urlTypeCode, String url) {
        this.url = url;
        this.urlTypeCode = urlTypeCode;
    }
}