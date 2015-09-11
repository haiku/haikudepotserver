/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.pkg;

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

    /**
     * <p>This relates to the repository requested.</p>
     */

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

        /**
         * @since 2015-06-22
         */

        public String repositorySourceCode;

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

        /**
         * <p>This represents when the package version was created in the HDS system.</p>
         * @since 2015-04-15
         */

        public Long createTimestamp;

        /**
         * <p>Is true if the package version has a source package available.</p>
         * @since 2015-09-10
         */

        public Boolean hasSource;

    }

}
