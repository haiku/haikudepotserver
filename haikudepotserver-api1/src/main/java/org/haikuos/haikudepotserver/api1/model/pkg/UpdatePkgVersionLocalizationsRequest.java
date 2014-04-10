/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class UpdatePkgVersionLocalizationsRequest {

    public String pkgName;

    public String architectureCode;

    /**
     * <p>Localizations may differ between architectures because the description etc... may be different.  More often
     * than not though, the localizations will be the same.  For this reason this flag is provided.  When set to true,
     * if the english variant that is being considered is the same as other architectures, then the localization will
     * also be replicated into the latest package for those architectures as well.</p>
     */

    public Boolean replicateToOtherArchitecturesWithSameEnglishContent;

    public List<PkgVersionLocalization> pkgVersionLocalizations;

}
