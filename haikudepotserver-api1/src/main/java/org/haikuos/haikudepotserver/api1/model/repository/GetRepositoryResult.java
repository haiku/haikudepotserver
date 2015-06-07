/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.repository;

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

    public String informationalUrl;

    public List<RepositorySource> repositorySources;

    public static class RepositorySource {

        public Boolean active;

        public String code;

        public String url;

    }

}
