/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.user;

import java.util.List;

public class UpdateUserRequest {

    public enum Filter {
        NATURALLANGUAGE,
        EMAIL,
        ACTIVE
    };

    public String nickname;

    public String naturalLanguageCode;

    public Boolean active;

    public String email;

    public List<Filter> filter;

}
