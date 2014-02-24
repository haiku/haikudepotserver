/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class GetPkgIconsResult {

    public List<GetPkgIconsResult.PkgIcon> pkgIcons;

    public static class PkgIcon {

        public String mediaTypeCode;
        public Integer size;

    }

}
