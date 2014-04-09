/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class GetBulkPkgResult {

    public List<Pkg> pkgs;

    public static class Pkg {

        public String name;

        /**
         * <p>This is the timestamp (millis since epoc) at which the package was last edited.  This is helpful for
         * situations where it is necessary to create a url that will cause the browser to refresh the data.</p>
         */

        public Long modifyTimestamp;

        public List<GetBulkPkgResult.PkgVersion> versions;

        public List<String> pkgCategoryCodes;

        public List<PkgScreenshot> pkgScreenshots;

        public List<PkgIcon> pkgIcons;

        public Float userRatingAverage;

    }

    public static class PkgVersion {

        public String major;
        public String minor;
        public String micro;
        public String preRelease;
        public Integer revision;

        /**
         * <p>In the request the client may have requested a specific natural language, but that may not have been
         * available.  This code indicates the natural language code that was <em>actually</em> used to obtain
         * material such as the summary and the description.</p>
         */

        public String naturalLanguageCode;

        public String summary;
        public String description;

        public Float userRatingAverage;

    }

}
