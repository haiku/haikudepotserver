/*
* Copyright 2014-2015, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.pkg;

import org.haiku.haikudepotserver.api1.model.PkgVersionType;

import java.util.List;

public class GetBulkPkgRequest {

    public enum Filter {
        PKGSCREENSHOTS,
        PKGCATEGORIES,
        PKGICONS,
        PKGVERSIONLOCALIZATIONDESCRIPTIONS,
        PKGCHANGELOG
    };

    public List<String> pkgNames;

    /**
     * <p>Data is returned in relation to the repository code supplied.</p>
     * @since 2015-05-27
     */

    public List<String> repositoryCodes;

    public List<String> architectureCodes;

    public PkgVersionType versionType;

    public String naturalLanguageCode;

    public List<Filter> filter;

}
