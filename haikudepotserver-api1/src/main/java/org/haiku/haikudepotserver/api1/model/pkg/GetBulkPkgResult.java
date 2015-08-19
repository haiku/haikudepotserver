/*
* Copyright 2014-2015, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.pkg;

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

        public Float derivedRating;

        public Integer prominenceOrdering;

    }

    public static class PkgVersion {

        public String major;
        public String minor;
        public String micro;
        public String preRelease;
        public Integer revision;

        public String architectureCode;

        /**
         * <p>This value is localized</p>
         * @since 2015-03-26
         */

        public String title;

        /**
         * @since 2015-04-31
         */

        public String repositorySourceCode;

        /**
         * @since 2015-04-31
         */

        public String repositoryCode;

        public String summary;
        public String description;

        public Long payloadLength;

    }

}
