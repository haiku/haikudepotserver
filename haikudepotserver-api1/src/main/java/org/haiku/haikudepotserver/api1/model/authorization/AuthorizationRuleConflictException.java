/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.authorization;

/**
 * <p>This exception is thrown in the case where adding a new rule is going to conflict with
 * another existing rule.</p>
 */

public class AuthorizationRuleConflictException extends Exception {

    public AuthorizationRuleConflictException() {
    }

}
