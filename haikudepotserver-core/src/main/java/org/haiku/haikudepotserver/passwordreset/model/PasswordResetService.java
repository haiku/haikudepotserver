/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset.model;

import org.haiku.haikudepotserver.passwordreset.PasswordResetException;

public interface PasswordResetService {

    // TODO; should be injected at some point as this should not know about the controller config.
    String URL_SEGMENT_PASSWORDRESET = "__passwordreset";

    /**
     * <p>This method will create the necessary tokens to reset a password and will dispatch those tokens
     * out to the users using their email address.  It is assumed that the email address is validated
     * by this point.</p>
     */

    void initiate(String email) throws PasswordResetException;

    /**
     * <p>This method will action the password reset token that the user would have supplied in a URL together with
     * a clear-text password.  This method will either perform the action or will not perform the action.  It will
     * not return if it has done anything or not, but it will log what it has done.</p>
     */

    void complete(String tokenCode, String passwordClear);

    /**
     * <p>This method will delete any tokens that have expired.</p>
     */

    void deleteExpiredPasswordResetTokens();

}
