/*
 * Copyright 2013-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.dataobjects.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * <p>This class is the superclass for objects that are involved in the request response cycle that would like to
 * obtain the currently authenticated user.  The currently authenticated user would have been authenticated using
 * a servlet filter and then would be available from a thread-local.</p>
 */

public abstract class AbstractUserAuthenticationAware {

    /**
     * <P>This method will get the currently authenticated user.  If there is no authenticated user then this method
     * will throw an instance of {@link AuthorizationFailureException}.</P>
     */

    protected User obtainAuthenticatedUser(ObjectContext objectContext) throws AuthorizationFailureException {
        return tryObtainAuthenticatedUser(objectContext).orElseThrow(AuthorizationFailureException::new);
    }

    /**
     * <P>This method will (optionally) return a user that represents the currently authenticated user.  It will not
     * return null.</P>
     */

    protected Optional<User> tryObtainAuthenticatedUser(ObjectContext objectContext) {
        Preconditions.checkArgument(null != objectContext, "the object context must be provided");
        return tryGetAuthenticatedUserObjectId().map((oid) -> User.getByObjectId(objectContext, oid));
    }

    private static Optional<ObjectId> tryGetAuthenticatedUserObjectId() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .map(Authentication::getPrincipal)
                .filter(p -> p instanceof ObjectId)
                .map(p -> (ObjectId) p);
    }

}
