/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security.model;

import org.haikuos.haikudepotserver.dataobjects.Permission;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.support.AbstractSearchSpecification;

import java.util.List;

public class AuthorizationPkgRuleSearchSpecification extends AbstractSearchSpecification {

    private User user;

    /**
     * <p>If permission codes are supplied then only rules related to those permission codes will be shown.  If this
     * field is blank then all permissions will be considered.</p>
     */

    private List<org.haikuos.haikudepotserver.dataobjects.Permission> permissions;

    private Pkg pkg;

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public List<Permission> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<Permission> permissions) {
        this.permissions = permissions;
    }

    public Pkg getPkg() {
        return pkg;
    }

    public void setPkg(Pkg pkg) {
        this.pkg = pkg;
    }

}
