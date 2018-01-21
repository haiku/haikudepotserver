/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import org.apache.cayenne.ObjectId;

import java.util.Optional;

/**
 * <p>This class provides helper code around authentication; a major function is the ability to relay the currently
 * authenticated user into a thread local for pick-up in other parts of the application.</p>
 */

public class AuthenticationHelper {

    private static ThreadLocal<ObjectId> authenticatedUserObjectIdHolder = new ThreadLocal<>();

    public static Optional<ObjectId> getAuthenticatedUserObjectId() {
        return Optional.ofNullable(authenticatedUserObjectIdHolder.get());
    }

    public static void setAuthenticatedUserObjectId(ObjectId value) {
        if (null != value) {
            authenticatedUserObjectIdHolder.set(value);
        } else {
            authenticatedUserObjectIdHolder.remove();
        }
    }

}
