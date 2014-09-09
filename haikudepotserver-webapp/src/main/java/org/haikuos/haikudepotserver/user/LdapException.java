/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

public class LdapException extends Exception {

    public LdapException(String message, Throwable cause) {
        super(message, cause);
    }
}
