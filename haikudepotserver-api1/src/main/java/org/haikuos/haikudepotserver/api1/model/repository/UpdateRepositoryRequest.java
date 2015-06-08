/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.repository;

import java.util.List;

public class UpdateRepositoryRequest {

    public enum Filter {
        ACTIVE,
        NAME,
        INFORMATIONURL
    }

    public String code;

    /**
     * @since 2015-06-08
     */

    public String name;

    public Boolean active;

    public String informationUrl;

    public List<Filter> filter;

}
