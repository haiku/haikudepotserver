/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class GetPkgVersionLocalizationsResult {

    public List<PkgVersionLocalization> pkgVersionLocalizations;

    public static class PkgVersionLocalization {

        public String naturalLanguageCode;

        public String summary;

        public String description;

    }

}
