/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class GetBulkPkgRequest {

    public enum Filter {
        PKGSCREENSHOTS,
        PKGCATEGORIES,
        PKGICONS,
        USERRATINGAVERAGES,
        PKGVERSIONLOCALIZATIONDESCRIPTIONS,
    };

    public List<String> pkgNames;

    public String architectureCode;

    public PkgVersionType versionType;

    public String naturalLanguageCode;

    public List<Filter> filter;

}
