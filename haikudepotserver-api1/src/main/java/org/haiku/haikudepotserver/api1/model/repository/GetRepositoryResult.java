/*
 * Copyright 2014-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import java.util.List;

public class GetRepositoryResult {

    public Boolean active;

    /**
     * @since 2015-07-07
     */

    public String name;

    public String code;

    public Long createTimestamp;

    public Long modifyTimestamp;

    public String informationUrl;

    /**
     * @since 2018-12-19
     */

    public Boolean hasPassword;

    /**
     * @since 2015-06-22
     */

    public List<RepositorySource> repositorySources;

    public static class RepositorySource {

        public Boolean active;

        public String code;

        public String url;

        /**
         * <p>This was previously (confusingly) called the <code>url</code>.</p>
         * @since 2020-06-15
         */

        public String identifier;

        /**
         * @since 2018-12-26
         */

        public Long lastImportTimestamp;

    }

}
