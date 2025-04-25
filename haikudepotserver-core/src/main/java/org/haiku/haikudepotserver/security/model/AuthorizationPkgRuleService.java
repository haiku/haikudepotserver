/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security.model;

import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.Permission;

import java.util.List;

/**
 * <p>This service is designed to orchestrate the authorization rules in the system.</p>
 */

public interface AuthorizationPkgRuleService {

    List<? extends AuthorizationPkgRule> search(
            ObjectContext context,
            AuthorizationPkgRuleSearchSpecification specification);

    long total(
            ObjectContext context,
            AuthorizationPkgRuleSearchSpecification specification);

    /**
     * <p>This method returns true if the proposed new rule would conflict with existing rules
     * that are already present in the system.</p>
     */

    boolean wouldConflict(
            ObjectContext context,
            User user,
            org.haiku.haikudepotserver.dataobjects.Permission permission,
            Pkg pkg);

    /**
     * <p>This method will create a new rule object.  It will decide what sort of object to create
     * based on the inputs supplied.</p>
     */

    AuthorizationPkgRule create(
            ObjectContext context,
            User user,
            Permission permission,
            Pkg pkg);

    void remove(
            ObjectContext context,
            User user,
            Permission permission,
            Pkg pkg);

}
