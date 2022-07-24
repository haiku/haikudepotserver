/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.CheckAuthorizationRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CheckAuthorizationResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateAuthorizationPkgRuleRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateAuthorizationPkgRuleResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.RemoveAuthorizationPkgRuleRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RemoveAuthorizationPkgRuleResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchAuthorizationPkgRulesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchAuthorizationPkgRulesResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class AuthorizationApiImpl extends AbstractApiImpl implements AuthorizationApi {

    private final AuthorizationApiService authorizationApiService;

    public AuthorizationApiImpl(AuthorizationApiService authorizationApiService) {
        this.authorizationApiService = Preconditions.checkNotNull(authorizationApiService);
    }

    @Override
    public ResponseEntity<CheckAuthorizationResponseEnvelope> checkAuthorization(CheckAuthorizationRequestEnvelope checkAuthorizationRequestEnvelope) {
        return ResponseEntity.ok(
                new CheckAuthorizationResponseEnvelope()
                        .result(authorizationApiService.checkAuthorization(checkAuthorizationRequestEnvelope)));
    }

    @Override
    public ResponseEntity<CreateAuthorizationPkgRuleResponseEnvelope> createAuthorizationPkgRule(CreateAuthorizationPkgRuleRequestEnvelope createAuthorizationPkgRuleRequestEnvelope) {
        authorizationApiService.createAuthorizationPkgRule(createAuthorizationPkgRuleRequestEnvelope);
        return ResponseEntity.ok(new CreateAuthorizationPkgRuleResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<RemoveAuthorizationPkgRuleResponseEnvelope> removeAuthorizationPkgRule(RemoveAuthorizationPkgRuleRequestEnvelope removeAuthorizationPkgRuleRequestEnvelope) {
        authorizationApiService.removeAuthorizationPkgRule(removeAuthorizationPkgRuleRequestEnvelope);
        return ResponseEntity.ok(new RemoveAuthorizationPkgRuleResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<SearchAuthorizationPkgRulesResponseEnvelope> searchAuthorizationPkgRules(SearchAuthorizationPkgRulesRequestEnvelope searchAuthorizationPkgRulesRequestEnvelope) {
        return ResponseEntity.ok(
                new SearchAuthorizationPkgRulesResponseEnvelope()
                        .result(authorizationApiService.searchAuthorizationPkgRules(searchAuthorizationPkgRulesRequestEnvelope)));
    }
}
