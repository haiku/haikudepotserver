/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security;

import com.google.common.base.Optional;
import org.apache.cayenne.ObjectId;

/**
 * <p>This class provides helper code around authentication; a major function is the ability to relay the currently
 * authenticated user into a thread local for pick-up in other parts of the application.</p>
 */

public class AuthenticationHelper {

    private static ThreadLocal<Optional<ObjectId>> authenticatedUserObjectIdHolder = new ThreadLocal<Optional<ObjectId>>();

    public static Optional<ObjectId> getAuthenticatedUserObjectId() {
        Optional<ObjectId> value = authenticatedUserObjectIdHolder.get();

        if(null==value) {
            value = Optional.absent();
        }

        return value;
    }

    public static void setAuthenticatedUserObjectId(Optional<ObjectId> value) {
        authenticatedUserObjectIdHolder.set(value);
    }

}
