/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.model.authorization;

import org.haiku.haikudepotserver.api1.support.AbstractSearchResult;

public class SearchAuthorizationPkgRulesResult extends AbstractSearchResult<SearchAuthorizationPkgRulesResult.Rule> {

    public static class Rule {

        public String userNickname;
        public String pkgName;
        public String permissionCode;

    }

}
