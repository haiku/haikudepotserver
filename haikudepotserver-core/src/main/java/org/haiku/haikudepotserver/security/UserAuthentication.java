/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectId;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collection;
import java.util.Set;

/**
 * <p>This {@link org.springframework.security.core.Authentication} represents a user in the Haiku
 * environment.</p>
 */

public class UserAuthentication implements org.springframework.security.core.Authentication {

    private final static String ROLE_USER = "ROLE_USER";

    private final ObjectId userObjectId;

    private boolean isAuthenticated = false;

    public UserAuthentication(ObjectId userObjectId) {
        this.userObjectId = Preconditions.checkNotNull(userObjectId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Set.of(new SimpleGrantedAuthority(ROLE_USER));
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
        return userObjectId;
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
        return "U[id:" + userObjectId.getIdSnapshot().get("id") + "]";
    }
}
