/*
 * Copyright 2015-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import java.util.List;

/**
 * @since 2015-06-09
 */

public class UpdateRepositorySourceRequest {

    public enum Filter {
        ACTIVE,
        FORCED_INTERNAL_BASE_URL
    }

    /**
     * <p>This is used to identify the repository source.</p>
     */

    public String code;

    public Boolean active;

    /**
     * @since 2018-12-18
     */

    public String forcedInternalBaseUrl;

    /**
     * <p>The filter controls what aspects of the repository source to alter.</p>
     */

    public List<Filter> filter;

}
