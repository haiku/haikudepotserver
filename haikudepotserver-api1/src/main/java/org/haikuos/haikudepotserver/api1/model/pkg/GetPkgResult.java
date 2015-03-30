/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

/**
 * <p>This is the result model that comes back from the get packages API invocation.</p>
 */

public class GetPkgResult {

    public String name;

    /**
     * <p>This is the timestamp (millis since epoc) at which the package was last edited.  This is helpful for
     * situations where it is necessary to create a url that will cause the browser to refresh the data.</p>
     */

    public Long modifyTimestamp;

    public List<GetPkgResult.PkgVersion> versions;

    public List<String> pkgCategoryCodes;

    public Float derivedRating;

    public Integer derivedRatingSampleSize;

    public Integer prominenceOrdering;

    public static class PkgVersion {

        public String major;
        public String minor;
        public String micro;
        public String preRelease;
        public Integer revision;

        public String architectureCode;
        public String summary;
        public String description;

        /**
         * <p>This title is localized.</p>
         * @since 2015-03-26
         */

        public String title;

        public String repositoryCode;

        public List<String> licenses;
        public List<String> copyrights;
        public List<PkgVersionUrl> urls;

        public Long viewCounter;

        public Boolean isLatest;

        /**
         * <p>The length in bytes of the package payload.</p>
         */

        public Long payloadLength;

    }

}
