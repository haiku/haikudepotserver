/*
 * Copyright 2021-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api2.model.AgreeUserUsageConditionsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.AgreeUserUsageConditionsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.ChangePasswordRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.ChangePasswordResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.CompletePasswordResetRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CompletePasswordResetResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateUserResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.InitiatePasswordResetRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.InitiatePasswordResetResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.RenewTokenRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RenewTokenResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUsersRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUsersResponseEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.UpdateUserResponseEnvelope;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;
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


}
