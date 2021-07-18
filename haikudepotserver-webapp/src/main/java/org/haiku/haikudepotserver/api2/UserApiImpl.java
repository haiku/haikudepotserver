/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import org.haiku.haikudepotserver.api1.model.user.*;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserResult;
import org.haiku.haikudepotserver.api2.model.GetUserResult;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsResult;
import org.haiku.haikudepotserver.api2.model.*;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;

import javax.validation.Valid;
import java.util.Optional;

@Controller
public class UserApiImpl extends AbstractApiImpl implements UserApi {

    private final org.haiku.haikudepotserver.api1.UserApiImpl userApiV1;

    public UserApiImpl(org.haiku.haikudepotserver.api1.UserApiImpl userApiV1) {
        this.userApiV1 = userApiV1;
    }

    @Override
    public ResponseEntity<AgreeUserUsageConditionsResponseEnvelope> agreeUserUsageConditions(@Valid AgreeUserUsageConditionsRequestEnvelope agreeUserUsageConditionsRequestEnvelope) {
        AgreeUserUsageConditionsRequest requestV1 = new AgreeUserUsageConditionsRequest();
        requestV1.nickname = agreeUserUsageConditionsRequestEnvelope.getNickname();
        requestV1.userUsageConditionsCode = agreeUserUsageConditionsRequestEnvelope.getUserUsageConditionsCode();
        userApiV1.agreeUserUsageConditions(requestV1);
        return ResponseEntity.ok(new AgreeUserUsageConditionsResponseEnvelope().result(new AgreeUserUsageConditionsResult()));
    }

    @Override
    public ResponseEntity<CreateUserResponseEnvelope> createUser(@Valid CreateUserRequestEnvelope createUserRequestEnvelope) {
        CreateUserRequest requestV1 = new CreateUserRequest();
        requestV1.nickname = createUserRequestEnvelope.getNickname();
        requestV1.passwordClear = createUserRequestEnvelope.getPasswordClear();
        requestV1.email = createUserRequestEnvelope.getEmail();
        requestV1.captchaToken = createUserRequestEnvelope.getCaptchaToken();
        requestV1.captchaResponse = createUserRequestEnvelope.getCaptchaResponse();
        requestV1.naturalLanguageCode = createUserRequestEnvelope.getNaturalLanguageCode();
        requestV1.userUsageConditionsCode = createUserRequestEnvelope.getUserUsageConditionsCode();
        userApiV1.createUser(requestV1);
        return ResponseEntity.ok(new CreateUserResponseEnvelope().result(new CreateUserResult()));
    }

    @Override
    public ResponseEntity<GetUserUsageConditionsResponseEnvelope> getUserUsageConditions(@Valid GetUserUsageConditionsRequestEnvelope getUserUsageConditionsRequestEnvelope) {
        org.haiku.haikudepotserver.api1.model.user.GetUserUsageConditionsRequest requestV1 = new GetUserUsageConditionsRequest();
        requestV1.code = getUserUsageConditionsRequestEnvelope.getCode();
        org.haiku.haikudepotserver.api1.model.user.GetUserUsageConditionsResult resultV1 = userApiV1.getUserUsageConditions(requestV1);
        return ResponseEntity.ok(new GetUserUsageConditionsResponseEnvelope().result(
                new GetUserUsageConditionsResult()
                        .code(resultV1.code)
                        .minimumAge(resultV1.minimumAge)));
    }

    @Override
    public ResponseEntity<GetUserResponseEnvelope> getUser(@Valid GetUserRequestEnvelope getUserRequestEnvelope) {
        GetUserRequest requestV1 = new GetUserRequest();
        requestV1.nickname = getUserRequestEnvelope.getNickname();
        org.haiku.haikudepotserver.api1.model.user.GetUserResult resultV1 = userApiV1.getUser(requestV1);
        return ResponseEntity.ok(new GetUserResponseEnvelope().result(mapV1ToV2(resultV1)));
    }

    @Override
    public ResponseEntity<AuthenticateUserResponseEnvelope> authenticateUser(AuthenticateUserRequestEnvelope authenticateUserRequestEnvelope) {
        AuthenticateUserRequest requestV1 = new AuthenticateUserRequest();
        requestV1.nickname = authenticateUserRequestEnvelope.getNickname();
        requestV1.passwordClear = authenticateUserRequestEnvelope.getPasswordClear();
        org.haiku.haikudepotserver.api1.model.user.AuthenticateUserResult resultV1 = userApiV1.authenticateUser(requestV1);
        return ResponseEntity.ok(
                new AuthenticateUserResponseEnvelope().result(
                        new AuthenticateUserResult().token(resultV1.token)
                ));
    }

    private GetUserResult mapV1ToV2(org.haiku.haikudepotserver.api1.model.user.GetUserResult resultV1) {
        return new GetUserResult()
                .nickname(resultV1.nickname)
                .email(resultV1.email)
                .active(resultV1.active)
                .isRoot(resultV1.isRoot)
                .createTimestamp(resultV1.createTimestamp)
                .modifyTimestamp(resultV1.modifyTimestamp)
                .naturalLanguageCode(resultV1.naturalLanguageCode)
                .lastAuthenticationTimestamp(resultV1.lastAuthenticationTimestamp)
                .userUsageConditionsAgreement(
                        Optional.ofNullable(resultV1.userUsageConditionsAgreement)
                            .map(uua -> new GetUserResultUserUsageConditionsAgreement()
                                    .timestampAgreed(uua.timestampAgreed)
                                    .userUsageConditionsCode(uua.userUsageConditionsCode)
                                    .isLatest(uua.isLatest))
                        .orElse(null));
    }


}
