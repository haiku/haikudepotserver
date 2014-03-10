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

    public List<Version> versions;

    public List<String> pkgCategoryCodes;

    public static class Version {

        public String major;
        public String minor;
        public String micro;
        public String preRelease;
        public Integer revision;

        public String architectureCode;
        public String summary;
        public String description;
        public String repositoryCode;

        public List<String> licenses;
        public List<String> copyrights;
        public List<Url> urls;

    }

    public static class Url {

        public String url;
        public String urlTypeCode;

    }

}
