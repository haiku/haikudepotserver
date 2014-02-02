/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import org.haikuos.haikudepotserver.api1.support.AbstractSearchResult;

public class SearchPkgsResult extends AbstractSearchResult<SearchPkgsResult.Pkg> {

    public static class Pkg {
        public String name;
        public Long modifyTimestamp;
        public Version version;
    }

    public static class Version {
        public String major;
        public String minor;
        public String micro;
        public String preRelease;
        public Integer revision;
    }

}
