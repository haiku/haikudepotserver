/*
 * Copyright 2014, Andrew Lindesay
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
         * <p>In the request the client may have requested a specific natural language, but that may not have been
         * available.  This code indicates the natural language code that was <em>actually</em> used to obtain
         * material such as the summary and the description.</p>
         */

        public String naturalLanguageCode;

        public String repositoryCode;

        public List<String> licenses;
        public List<String> copyrights;
        public List<PkgVersionUrl> urls;

        public Long viewCounter;

        public Boolean isLatest;

    }

}
