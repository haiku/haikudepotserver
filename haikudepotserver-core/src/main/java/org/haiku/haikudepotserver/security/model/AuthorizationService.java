/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security.model;

import org.apache.cayenne.DataObject;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.User;

public interface AuthorizationService {

    /**
     * <p>Returns true if the user supplied has the permission over the target object.</p>
     */

    boolean check(
            ObjectContext objectContext,
            User authenticatedUser,
            TargetType targetType,
            String targetIdentifier,
            Permission permission);

    /**
     * <p>This method will return true if the permission applies in this situation.</p>
     */

    boolean check(
            ObjectContext objectContext,
            User authenticatedUser,
            DataObject target,
            final Permission permission);

}
