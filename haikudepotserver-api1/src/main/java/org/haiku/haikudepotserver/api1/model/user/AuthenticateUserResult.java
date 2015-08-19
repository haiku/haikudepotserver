/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.user;

public class AuthenticateUserResult {

    /**
     * <p>In the case of a successful authentication, this field will be non-null and will contain a standard
     * formatted json-web-token.  If the authentication had failed then this token will be null.</p>
     */

    public String token;

}
