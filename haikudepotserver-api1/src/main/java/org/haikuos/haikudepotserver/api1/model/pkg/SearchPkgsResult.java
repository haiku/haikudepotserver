/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import java.util.List;

public class SearchPkgsResult {

    public List<Pkg> pkgs;

    public Boolean hasMore;

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
