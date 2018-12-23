/*
 * Copyright 2014-2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.repository;

import java.util.List;

public class UpdateRepositoryRequest {

    public enum Filter {
        ACTIVE,
        NAME,
        INFORMATIONURL,
        PASSWORD
    }

    public String code;

    /**
     * @since 2015-06-08
     */

    public String name;

    public Boolean active;

    public String informationUrl;

    /**
     * <p>If this field is NULL or the empty string then the password will
     * be cleared.</p>
     * @since 2018-12-20
     */

    public String passwordClear;

    public List<Filter> filter;

}
