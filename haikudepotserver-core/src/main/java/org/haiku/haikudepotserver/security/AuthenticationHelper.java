/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.haiku.haikudepotserver.dataobjects.User;
import org.springframework.security.core.Authentication;

import java.util.Optional;

public class AuthenticationHelper {

    public static Optional<User> tryGetUserForAuthentication(ObjectContext context, Authentication authentication) {
        Preconditions.checkNotNull(context);
        return Optional.ofNullable(authentication)
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(p -> p instanceof ObjectId)
                .map(p -> (ObjectId) p)
                .map(oid -> User.getByObjectId(context, oid));
    }

}
