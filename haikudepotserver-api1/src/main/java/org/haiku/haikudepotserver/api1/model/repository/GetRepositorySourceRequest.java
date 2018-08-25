/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

/**
 * @since 2015-06-09
 */

public class GetRepositorySourceRequest {

    public String code;

    /**
     * @since 2018-08-25
     */

    public Boolean includeInactiveRepositorySourceMirrors;

}
