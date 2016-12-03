/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.*;

import java.util.regex.Pattern;

public interface PkgLocalizationService {

    String SUFFIX_SUMMARY_DEVELOPMENT = " (development files)";

    /**
     * <p>For a given package version, this method will look at the various levels of localization and fallback
     * options to English and will produce an object that represents the best language options.</p>
     *
     * <p>If the pattern is provided, any localization for the provided natural language will be taken first if
     * it matches, otherwise the english version will be tried.</p>
     */

    ResolvedPkgVersionLocalization resolvePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            Pattern searchPattern,
            NaturalLanguage naturalLanguage);

    /**
     * <p>This method will update the localization defined in the parameters to this method into the data
     * structure for the package.</p>
     */

    PkgLocalization updatePkgLocalization(
            ObjectContext context,
            Pkg pkg,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description);

    void replicatePkgLocalizations(
            ObjectContext context,
            Pkg sourcePkg,
            Pkg targetPkg,
            String summarySuffix);

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
