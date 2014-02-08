/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.miscellaneous;

import org.haikuos.haikudepotserver.api1.model.AuthorizationTargetType;

import java.util.List;

public class CheckAuthorizationRequest {

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

        public AuthorizationTargetAndPermission() {
        }

        public AuthorizationTargetAndPermission(AuthorizationTargetType targetType, String targetIdentifier, String permissionCode) {
            this.targetType = targetType;
            this.targetIdentifier = targetIdentifier;
            this.permissionCode = permissionCode;
        }
    }

}
