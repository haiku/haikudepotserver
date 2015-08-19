/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg;

import java.util.List;

public class ReorderPkgScreenshotsRequest {

    public String pkgName;

    /**
     * <p>This is an ordered list of codes that describe the ordering desired for the screenshots of this package.</p>
     */

    public List<String> codes;

    public ReorderPkgScreenshotsRequest() {
    }

    public ReorderPkgScreenshotsRequest(String pkgName, List<String> codes) {
        this.pkgName = pkgName;
        this.codes = codes;
    }

}
