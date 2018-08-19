/*
 * Copyright 2015-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import java.util.List;

/**
 * @since 2015-06-09
 */

public class GetRepositorySourceResult {

    public String code;

    public String repositoryCode;

    public Boolean active;

    /**
     * <p>Previous releases of this API used this field as the base-url for the
     * repository.  Now this field, as with the C/C++ equivalent is the
     * identifier URL for the repository itself.  This URL will be the same
     * across mirrors.</p>
     */

    public String url;

    /**
     * @since 2018-07-28
     */

    public List<RepositorySourceMirror> repositorySourceMirrors;

    public static class RepositorySourceMirror {

        public Boolean active;

        public String countryCode;

        public String code;

        public String baseUrl;

        public Boolean isPrimary;

    }

}
