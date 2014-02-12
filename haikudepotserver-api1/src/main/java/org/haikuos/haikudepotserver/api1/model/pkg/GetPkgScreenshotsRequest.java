/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

public class GetPkgScreenshotsRequest {

    public String pkgName;

    public GetPkgScreenshotsRequest() {
    }

    public GetPkgScreenshotsRequest(String pkgName) {
        this.pkgName = pkgName;
    }
}
