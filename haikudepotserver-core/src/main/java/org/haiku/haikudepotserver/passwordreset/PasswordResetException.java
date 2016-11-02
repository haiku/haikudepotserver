/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset;

public class PasswordResetException extends Exception {

    public PasswordResetException(String message) {
        super(message);
    }

    public PasswordResetException(String message, Throwable cause) {
        super(message, cause);
    }
}
