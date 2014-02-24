/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class ConfigurePkgIconRequest {

    public String pkgName;

    public List<PkgIcon> pkgIcons;

    public static class PkgIcon {

        public String mediaTypeCode;
        public Integer size;
        public String dataBase64;

        public PkgIcon() {
        }

        public PkgIcon(String mediaTypeCode, Integer size, String dataBase64) {
            this.mediaTypeCode = mediaTypeCode;
            this.size = size;
            this.dataBase64 = dataBase64;
        }
    }

}
