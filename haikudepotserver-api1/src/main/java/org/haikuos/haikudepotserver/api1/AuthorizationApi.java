/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.googlecode.jsonrpc4j.JsonRpcService;
import org.haikuos.haikudepotserver.api1.model.authorization.*;
import org.haikuos.haikudepotserver.api1.model.authorization.CreateAuthorizationPkgRuleRequest;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;

/**
 * <p>API related to authorization.</p>
 */

@JsonRpcService("/api/v1/authorization")
public interface AuthorizationApi {

    /**
     * <p>This method will take in a list of permissions with targets and will return the list of those that
     * pass authorization checks against the presently authenticated user.</p>
     */

    CheckAuthorizationResult checkAuthorization(CheckAuthorizationRequest deriveAuthorizationRequest);

    /**
     * <p>This method will create a new authorization rule.  It will do this based on the data encapsulated in
     * the request.</p>
     */

    CreateAuthorizationPkgRuleResult createAuthorizationPkgRule(CreateAuthorizationPkgRuleRequest request) throws ObjectNotFoundException, AuthorizationRuleConflictException;

    /**
     * <p>This method will delete an authorization rule identified by the coordinates in the request.  If it
     * was not able to find the rule to delete then it will thrown an instance of
     * {@link org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException}.</p>
     */

    RemoveAuthorizationPkgRuleResult removeAuthorizationPkgRule(RemoveAuthorizationPkgRuleRequest request) throws ObjectNotFoundException;

    SearchAuthorizationPkgRulesResult searchAuthorizationPkgRules(SearchAuthorizationPkgRulesRequest request) throws ObjectNotFoundException;


}
