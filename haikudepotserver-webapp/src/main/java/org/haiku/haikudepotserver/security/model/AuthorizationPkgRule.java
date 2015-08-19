/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security.model;

import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;

public interface AuthorizationPkgRule {

    org.haiku.haikudepotserver.dataobjects.Permission getPermission();

    User getUser();

    Pkg getPkg();

}
