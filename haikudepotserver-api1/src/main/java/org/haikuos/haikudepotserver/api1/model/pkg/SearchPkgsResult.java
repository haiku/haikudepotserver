/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

import org.haikuos.haikudepotserver.api1.support.AbstractSearchResult;

import java.util.List;

public class SearchPkgsResult extends AbstractSearchResult<SearchPkgsResult.Pkg> {

    public static class Pkg {
        public String name;
        public Long modifyTimestamp;

        /**
         * <p>This versions value should only contain the one item actually, but is
         * provided in this form to retain consistency with other API.</p>
         */

        public List<PkgVersion> versions;
        public Float derivedRating;
    }

    public static class PkgVersion {
        public String major;
        public String minor;
        public String micro;
        public String preRelease;
        public Integer revision;
        public Long createTimestamp;
        public Long viewCounter;
        public String architectureCode;
    }

}
