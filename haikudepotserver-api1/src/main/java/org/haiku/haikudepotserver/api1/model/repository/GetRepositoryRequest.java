/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

public class GetRepositoryRequest {

    public String code;

    /**
     * @since 2015-06-08
     */

    public Boolean includeInactiveRepositorySources;

}
