/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security.model;

import org.haikuos.haikudepotserver.api1.model.AuthorizationTargetType;

public enum Permission {

    REPOSITORY_VIEW(AuthorizationTargetType.REPOSITORY),
    REPOSITORY_EDIT(AuthorizationTargetType.REPOSITORY),
    REPOSITORY_IMPORT(AuthorizationTargetType.REPOSITORY),
    REPOSITORY_LIST(null),
    REPOSITORY_LIST_INACTIVE(null),

    USER_VIEW(AuthorizationTargetType.USER),
    USER_EDIT(AuthorizationTargetType.USER),
    USER_CHANGEPASSWORD(AuthorizationTargetType.USER),
    USER_LIST(null),

    PKG_EDITICON(AuthorizationTargetType.PKG);

    private AuthorizationTargetType requiredTargetType;

    Permission(AuthorizationTargetType requiredTargetType) {
        this.requiredTargetType = requiredTargetType;
    }

    public AuthorizationTargetType getRequiredTargetType() {
        return requiredTargetType;
    }

}
