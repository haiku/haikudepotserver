/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.dataobjects.User;

import java.util.Optional;

public class UserPkgSupplementModificationAgent implements PkgSupplementModificationAgent {

    private final User user;

    public UserPkgSupplementModificationAgent(User user) {
        this.user = user;
    }

    @Override
    public User getUser() {
        return user;
    }

    @Override
    public String getUserDescription() {
        return Optional.ofNullable(user).map(User::getNickname).orElse(null);
    }

    @Override
    public String getOriginSystemDescription() {
        return PkgSupplementModificationAgent.HDS_ORIGIN_SYSTEM_DESCRIPTION;
    }
}
