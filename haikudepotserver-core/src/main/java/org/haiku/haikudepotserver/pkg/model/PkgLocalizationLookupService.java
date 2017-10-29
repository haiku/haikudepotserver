/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;

import java.util.regex.Pattern;

public interface PkgLocalizationLookupService {

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

}
