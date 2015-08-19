/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.user;

import org.haiku.haikudepotserver.api1.support.AbstractSearchResult;

public class SearchUsersResult extends AbstractSearchResult<SearchUsersResult.User> {

    public static class User {

        public String nickname;
        public Boolean active;

    }

}
