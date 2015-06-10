/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.repository;

import java.util.List;

/**
 * @since 2015-06-09
 */

public class UpdateRepositorySourceRequest {

    public enum Filter {
        ACTIVE,
        URL,
    }

    /**
     * <p>This is used to identify the repository source.</p>
     */

    public String code;

    public Boolean active;

    public String url;

    /**
     * <p>The filter controls what aspects of the repository source to alter.</p>
     */

    public List<Filter> filter;

}
