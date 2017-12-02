/*
 * Copyright 2015-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

/**
 * @since 2015-06-09
 */

public class GetRepositorySourceResult {

    public String code;

    public String repositoryCode;

    public Boolean active;

    public String url;

    /**
     * @since 2017-12-02
     */

    public String repoInfoUrl;

}
