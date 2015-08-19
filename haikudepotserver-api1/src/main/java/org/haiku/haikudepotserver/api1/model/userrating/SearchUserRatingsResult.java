/*
* Copyright 2014, Andrew Lindesay
* Distributed under the terms of the MIT License.
*/

package org.haiku.haikudepotserver.api1.model.userrating;

import org.haiku.haikudepotserver.api1.support.AbstractSearchResult;

public class SearchUserRatingsResult extends AbstractSearchResult<SearchUserRatingsResult.UserRating> {

    public static class UserRating {

        public String code;

        public String naturalLanguageCode;

        public AbstractUserRatingResult.User user;

        public String userRatingStabilityCode;

        public Boolean active;

        public String comment;

        public Long modifyTimestamp;

        public Long createTimestamp;

        public Short rating;

        public AbstractUserRatingResult.PkgVersion pkgVersion;

    }

}
