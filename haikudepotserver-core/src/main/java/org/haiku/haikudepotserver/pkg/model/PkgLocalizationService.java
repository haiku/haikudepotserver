/*
 * Copyright 2016-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.*;

public interface PkgLocalizationService extends PkgLocalizationLookupService {

    /**
     * <p>This method will update the localization defined in the parameters to this method into the data
     * structure for the package.</p>
     */

    PkgLocalization updatePkgLocalization(
            ObjectContext context,
            PkgSupplement pkgSupplement,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description);

    /**
     * <p>This method will either find the existing localization or create a new one.  It will then set the localized
     * values for the package.  Note that null or empty values will be treated the same and these values will be
     * trimmed.  Note that if the summary and description are null or empty string then if there is an existing
     * localization value, that this localization value will be deleted.</p>
     */

    PkgVersionLocalization updatePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description);

}
