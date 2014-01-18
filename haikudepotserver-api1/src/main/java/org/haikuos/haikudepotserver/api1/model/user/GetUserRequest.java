/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.user;

public class GetUserRequest {

    public String nickname;

    public GetUserRequest() {
    }

    public GetUserRequest(String nickname) {
        if(null==nickname || 0==nickname.length()) {
            throw new IllegalStateException("the nickname must be supplied to get a user");
        }

        this.nickname = nickname;
    }
}
