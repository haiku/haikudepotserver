/*
 * Copyright 2021-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import jakarta.validation.Valid;
import org.haiku.haikudepotserver.api2.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import java.util.Map;

@Controller
public class UserApiImpl extends AbstractApiImpl implements UserApi {

    private final UserApiService userApiService;

    public UserApiImpl(UserApiService userApiService) {
        this.userApiService = userApiService;
    }

    @Override
    public ResponseEntity<AgreeUserUsageConditionsResponseEnvelope> agreeUserUsageConditions(@Valid AgreeUserUsageConditionsRequestEnvelope request) {
        userApiService.agreeUserUsageConditions(request);
        return ResponseEntity.ok(new AgreeUserUsageConditionsResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<CreateUserResponseEnvelope> createUser(@Valid CreateUserRequestEnvelope request) {
        userApiService.createUser(request);
        return ResponseEntity.ok(new CreateUserResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<GetUserUsageConditionsResponseEnvelope> getUserUsageConditions(@Valid GetUserUsageConditionsRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetUserUsageConditionsResponseEnvelope().result(
                        userApiService.getUserUsageConditions(request)));
    }

    @Override
    public ResponseEntity<InitiatePasswordResetResponseEnvelope> initiatePasswordReset(InitiatePasswordResetRequestEnvelope request) {
        userApiService.initiatePasswordReset(request);
        return ResponseEntity.ok(new InitiatePasswordResetResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<RenewTokenResponseEnvelope> renewToken(RenewTokenRequestEnvelope request) {
        return ResponseEntity.ok(
                new RenewTokenResponseEnvelope()
                        .result(userApiService.renewToken(request)));
    }

    @Override
    public ResponseEntity<SearchUsersResponseEnvelope> searchUsers(SearchUsersRequestEnvelope request) {
        return ResponseEntity.ok(
                new SearchUsersResponseEnvelope()
                        .result(userApiService.searchUsers(request)));
    }

    @Override
    public ResponseEntity<UpdateUserResponseEnvelope> updateUser(UpdateUserRequestEnvelope request) {
        userApiService.updateUser(request);
        return ResponseEntity.ok(new UpdateUserResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<GetUserResponseEnvelope> getUser(@Valid GetUserRequestEnvelope request) {
        return ResponseEntity.ok(
                new GetUserResponseEnvelope()
                    .result(userApiService.getUser(request)));
    }

    @Override
    public ResponseEntity<GetCurrentUserResponseEnvelope> getCurrentUser(@Valid Object body) {
        return ResponseEntity.ok(
                new GetCurrentUserResponseEnvelope()
                        .result(userApiService.getCurrentUser()));
    }


    @Override
    public ResponseEntity<AuthenticateUserResponseEnvelope> authenticateUser(AuthenticateUserRequestEnvelope request) {
        return ResponseEntity.ok(
                new AuthenticateUserResponseEnvelope()
                        .result(userApiService.authenticateUser(request)));
    }

    @Override
    public ResponseEntity<ChangePasswordResponseEnvelope> changePassword(ChangePasswordRequestEnvelope request) {
        userApiService.changePassword(request);
        return ResponseEntity.ok(new ChangePasswordResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<CompletePasswordResetResponseEnvelope> completePasswordReset(CompletePasswordResetRequestEnvelope request) {
        userApiService.completePasswordReset(request);
        return ResponseEntity.ok(new CompletePasswordResetResponseEnvelope().result(Map.of()));
    }

    @Override
    public ResponseEntity<GetPasswordRequirementsResponseEnvelope> getPasswordRequirements(Object body) {
        return ResponseEntity.ok(
                new GetPasswordRequirementsResponseEnvelope()
                        .result(userApiService.getPasswordRequirements()));
    }

}
