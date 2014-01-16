/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.user;

/**
 * <p>This is the request model object for changing a password.  For details on the captcha token, see the documentation
 * supplied on {@link org.haikuos.haikudepotserver.api1.model.user.CreateUserRequest}.</p>
 */

public class ChangePasswordRequest {

    public String nickname;
    public String oldPasswordClear;
    public String newPasswordClear;
    public String captchaToken;
    public String captchaResponse;

}
