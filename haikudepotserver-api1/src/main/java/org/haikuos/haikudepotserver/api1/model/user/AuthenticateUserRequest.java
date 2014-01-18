/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.user;

public class AuthenticateUserRequest {

    public String nickname;
    public String passwordClear;

    public AuthenticateUserRequest() {
    }

    public AuthenticateUserRequest(String nickname, String passwordClear) {

        if(null==nickname||0==nickname.length()) {
            throw new IllegalStateException("the nickname must be supplied to authenticate a user");
        }

        if(null==passwordClear||0==passwordClear.length()) {
            throw new IllegalStateException("the password (clear) must be supplied to authenticate a user");
        }

        this.nickname = nickname;
        this.passwordClear = passwordClear;
    }
}
