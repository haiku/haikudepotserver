/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.userrating;

import java.util.List;

public class UpdateUserRatingRequest {

    public enum Filter {
        ACTIVE,
        NATURALLANGUAGE,
        USERRATINGSTABILITY,
        COMMENT,
        RATING
    };

    public String code;

    public Boolean active;

    public String naturalLanguageCode;

    public String userRatingStabilityCode;

    public String comment;

    public Short rating;

    public List<Filter> filter;

}
