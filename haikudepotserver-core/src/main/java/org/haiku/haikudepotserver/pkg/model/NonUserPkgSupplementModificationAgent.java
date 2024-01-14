/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.dataobjects.User;

public class NonUserPkgSupplementModificationAgent implements PkgSupplementModificationAgent {

    private final String userDescription;

    private final String originSystemDescription;

    public NonUserPkgSupplementModificationAgent(String userDescription, String originSystemDescription) {
        this.userDescription = userDescription;
        this.originSystemDescription = originSystemDescription;
    }

    @Override
    public User getUser() {
        return null;
    }

    @Override
    public String getUserDescription() {
        return userDescription;
    }

    @Override
    public String getOriginSystemDescription() {
        return originSystemDescription;
    }

}
