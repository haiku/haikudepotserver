/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.pkg;

public class PkgIcon {

    public String mediaTypeCode;
    public Integer size;

    public PkgIcon() {
    }

    public PkgIcon(String mediaTypeCode, Integer size) {
        this.mediaTypeCode = mediaTypeCode;
        this.size = size;
    }

}
