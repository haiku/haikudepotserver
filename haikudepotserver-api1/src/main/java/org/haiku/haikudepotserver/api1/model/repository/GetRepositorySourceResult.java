/*
 * Copyright 2015-2018, Andrew Lindesay
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
     * <p>Historically this was called the <code>url</code>, but the name was
     * changed in 2020.</p>
     * @since 2020-06-15
     */

    public String identifier;

    /**
     * <p>This URL is only shown to some users.  If it is not able to be
     * provided in the current authentication context, it will be NULL.</p>
     * @since 2018-12-18
     */

    public String forcedInternalBaseUrl;

    /**
     * @since 2018-12-26
     */

    public Long lastImportTimestamp;

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
