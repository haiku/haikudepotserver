/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security.model;

import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;

public interface AuthorizationPkgRule {

    org.haikuos.haikudepotserver.dataobjects.Permission getPermission();

    User getUser();

    Pkg getPkg();

}
