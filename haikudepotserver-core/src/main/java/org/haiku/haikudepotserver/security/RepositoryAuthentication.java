/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;

/**
 * <p>This represents a user who has authenticated just to access repository operations.</p>
 */

public class RepositoryAuthentication implements org.springframework.security.core.Authentication {

    private final static String ROLE_REPOSITORY_OPERATION = "ROLE_REPOSITORY_OP";

    private final String repositoryCode;

    private boolean isAuthenticated = false;

    public RepositoryAuthentication(String repositoryCode) {
        Preconditions.checkArgument(StringUtils.isNotBlank(repositoryCode));
        this.repositoryCode = repositoryCode;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Set.of(new SimpleGrantedAuthority(ROLE_REPOSITORY_OPERATION));
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getDetails() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return repositoryCode;
    }

    @Override
    public boolean isAuthenticated() {
        return isAuthenticated;
    }

    @Override
    public void setAuthenticated(boolean value) throws IllegalArgumentException {
        this.isAuthenticated = value;
    }

    @Override
    public String getName() {
        return "RO[code:" + repositoryCode + "]";
    }

}
