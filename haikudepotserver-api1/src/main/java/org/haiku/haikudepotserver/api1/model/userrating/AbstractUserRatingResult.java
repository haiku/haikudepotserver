/*
 * Copyright 2014-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.userrating;
@Deprecated
public class AbstractUserRatingResult {

    public static class PkgVersion {

        /**
         * @since 2015-07-07
         * @deprecated please use {@link #repositorySourceCode} instead.
         */

        public String repositoryCode;

        /**
         * @since 2022-03-24
         */

        public String repositorySourceCode;

        public String architectureCode;

        public String major;

        public String minor;

        public String micro;

        public String preRelease;

        public Integer revision;

        public Pkg pkg;

    }

    public static class Pkg {

        public String name;

    }

    public static class User {

        public String nickname;

    }

}
