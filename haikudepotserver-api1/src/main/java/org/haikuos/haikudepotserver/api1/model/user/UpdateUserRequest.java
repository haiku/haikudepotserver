/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.user;

import java.util.List;

public class UpdateUserRequest {

    public enum Filter {
        NATURALLANGUAGE
    };

    public String nickname;

    public String naturalLanguageCode;

    public List<Filter> filter;

}
