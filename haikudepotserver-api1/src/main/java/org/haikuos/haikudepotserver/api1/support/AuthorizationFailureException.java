/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.support;

/**
 * <P>This exception is thrown in the case where the user has attempted to do something, but are not allowed to.</P>
 */

public class AuthorizationFailureException extends RuntimeException {

    public AuthorizationFailureException() {
        super();
    }

}