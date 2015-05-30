/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.repository;

import java.util.List;

public class UpdateRepositoryRequest {

    public enum Filter {
        ACTIVE,
        INFORMATIONALURL
    }

    public String code;

    public Boolean active;

    public String informationalUrl;

    public List<Filter> filter;

}
