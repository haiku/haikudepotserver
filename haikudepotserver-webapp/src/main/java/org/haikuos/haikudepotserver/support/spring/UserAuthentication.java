/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.spring;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectId;
import org.haikuos.haikudepotserver.model.User;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Collections;

/**
 * <p>This authentication implementation is able to hold the principal as a Cayenne ObjectId so that the user is
 * relatively inexpensively able to be obtained later when the authenticated user is required without another
 * database trip.  This means that the system is locked into the Cayenne ORM, but the benefit here seems quite
 * good. See {@link org.haikuos.haikudepotserver.api1.AbstractApiImpl} for an example of where this is used.</p>
 */

public class UserAuthentication implements Authentication {

    private String userNickname;
    private ObjectId userObjectId;
    private boolean isAuthenticated = false;

    public UserAuthentication(User user) {
        Preconditions.checkNotNull(user);
        userNickname = user.getNickname();
        userObjectId = user.getObjectId();
        isAuthenticated = true;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.emptySet();
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
    public void setAuthenticated(boolean isAuthenticated) throws IllegalArgumentException {
        this.isAuthenticated = isAuthenticated;
    }

    @Override
    public String getName() {
        return userNickname;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        UserAuthentication that = (UserAuthentication) o;

        if (!userNickname.equals(that.userNickname)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return userNickname.hashCode();
    }

    @Override
    public String toString() {
        return  "auth; "+userNickname;
    }

}
