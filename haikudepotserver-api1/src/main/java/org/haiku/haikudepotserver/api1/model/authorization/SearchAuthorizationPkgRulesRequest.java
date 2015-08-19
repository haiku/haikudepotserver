/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.authorization;

import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;

import java.util.List;

public class SearchAuthorizationPkgRulesRequest extends AbstractSearchRequest {

    public String userNickname;

    /**
     * <p>If permission codes are supplied then only rules related to those permission codes will be shown.  If this
     * field is blank then all permissions will be considered.</p>
     */

    public List<String> permissionCodes;

    public String pkgName;

}
