/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.api2.model.QueueAuthorizationRulesSpreadsheetResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

@Controller
public class AuthorizationJobApiImpl extends AbstractApiImpl implements AuthorizationJobApi {

    private final AuthorizationJobApiService authorizationJobApiService;

    public AuthorizationJobApiImpl(AuthorizationJobApiService authorizationJobApiService) {
        this.authorizationJobApiService = Preconditions.checkNotNull(authorizationJobApiService);
    }

    @Override
    public ResponseEntity<QueueAuthorizationRulesSpreadsheetResponseEnvelope> queueAuthorizationRulesSpreadsheet(Object body) {
        return ResponseEntity.ok(
                new QueueAuthorizationRulesSpreadsheetResponseEnvelope()
                        .result(authorizationJobApiService.queueAuthorizationRulesSpreadsheet()));

    }

}
