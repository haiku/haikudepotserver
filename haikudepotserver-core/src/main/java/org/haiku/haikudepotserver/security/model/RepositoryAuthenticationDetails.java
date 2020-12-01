/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security.model;

import com.google.common.base.Preconditions;

/**
 * <p>When an authentication happens that is for a repository then this data is stored in the
 * {@link org.springframework.security.core.Authentication}'s user details to denote for which
 * repository the authentication was for.</p>
 */

public class RepositoryAuthenticationDetails {

    private final String repositoryCode;

    public RepositoryAuthenticationDetails(String repositoryCode) {
        this.repositoryCode = Preconditions.checkNotNull(repositoryCode);
    }

    public String getRepositoryCode() {
        return repositoryCode;
    }
}
