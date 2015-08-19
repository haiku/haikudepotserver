/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.authorization;

import java.util.List;

public class CheckAuthorizationResult {

    public List<AuthorizationTargetAndPermission> targetAndPermissions;

    public static class AuthorizationTargetAndPermission {

        /**
         * <p>The target type defines what sort of object you want to check your authorization for.  The #targetIdentifier
         * then identifies an instance of that type.</p>
         */

        public AuthorizationTargetType targetType;

        /**
         * This identifier will identify an instance of the #targetType that has the authorization applied to it.  Some
         * permissions may not require a target identifier; in which case this value can be supplied as null.
         */

        public String targetIdentifier;

        /**
         * <p>This is a list of permissions that the client would like to check for in the context of the target
         * identified by other parameters in this request.</p>
         */

        public String permissionCode;

        /**
         * <p>This boolean will be true if the target is authorized; false if not.</p>
         */

        public Boolean authorized;
    }


}
