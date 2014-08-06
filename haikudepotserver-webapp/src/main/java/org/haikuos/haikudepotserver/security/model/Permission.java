/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security.model;

public enum Permission {

    REPOSITORY_VIEW(TargetType.REPOSITORY),
    REPOSITORY_EDIT(TargetType.REPOSITORY),
    REPOSITORY_IMPORT(TargetType.REPOSITORY),
    REPOSITORY_LIST(null),
    REPOSITORY_LIST_INACTIVE(null),
    REPOSITORY_ADD(null),

    USER_VIEW(TargetType.USER),
    USER_EDIT(TargetType.USER),
    USER_CHANGEPASSWORD(TargetType.USER),
    USER_LIST(null),

    AUTHORIZATION_CONFIGURE(null),

    USERRATING_EDIT(TargetType.USERRATING),

    PKG_CREATEUSERRATING(TargetType.PKG),
    PKG_EDITICON(TargetType.PKG),
    PKG_EDITSCREENSHOT(TargetType.PKG),
    PKG_EDITCATEGORIES(TargetType.PKG),
    PKG_EDITVERSIONLOCALIZATION(TargetType.PKG),
    PKG_EDITPROMINENCE(TargetType.PKG);

    private TargetType requiredTargetType;

    Permission(TargetType requiredTargetType) {
        this.requiredTargetType = requiredTargetType;
    }

    public TargetType getRequiredTargetType() {
        return requiredTargetType;
    }

}
