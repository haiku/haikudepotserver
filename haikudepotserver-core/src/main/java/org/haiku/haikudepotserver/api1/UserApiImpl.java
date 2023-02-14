/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api1.model.user.AgreeUserUsageConditionsRequest;
import org.haiku.haikudepotserver.api1.model.user.AgreeUserUsageConditionsResult;
import org.haiku.haikudepotserver.api1.model.user.AuthenticateUserRequest;
import org.haiku.haikudepotserver.api1.model.user.AuthenticateUserResult;
import org.haiku.haikudepotserver.api1.model.user.CreateUserRequest;
import org.haiku.haikudepotserver.api1.model.user.CreateUserResult;
import org.haiku.haikudepotserver.api1.model.user.GetUserRequest;
import org.haiku.haikudepotserver.api1.model.user.GetUserResult;
import org.haiku.haikudepotserver.api1.model.user.GetUserUsageConditionsRequest;
import org.haiku.haikudepotserver.api1.model.user.GetUserUsageConditionsResult;
import org.haiku.haikudepotserver.api2.UserApiService;
import org.haiku.haikudepotserver.api2.model.AgreeUserUsageConditionsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserResultUserUsageConditionsAgreement;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsRequestEnvelope;
import org.springframework.stereotype.Component;

@Deprecated
@Component("userApiImplV1")
public class UserApiImpl implements UserApi {

    private final UserApiService userApiService;

    public UserApiImpl(UserApiService userApiService) {
        this.userApiService = Preconditions.checkNotNull(userApiService);
    }

    @Override
    public CreateUserResult createUser(CreateUserRequest createUserRequest) {

        Preconditions.checkNotNull(createUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.nickname));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.passwordClear));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.captchaToken));
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.captchaResponse),"a capture response is required to create a user");
        Preconditions.checkState(!Strings.isNullOrEmpty(createUserRequest.naturalLanguageCode));

        userApiService.createUser(new CreateUserRequestEnvelope()
                .nickname(createUserRequest.nickname)
                .passwordClear(createUserRequest.passwordClear)
                .email(createUserRequest.email)
                .captchaToken(createUserRequest.captchaToken)
                .captchaResponse(createUserRequest.captchaResponse)
                .naturalLanguageCode(createUserRequest.naturalLanguageCode)
                .userUsageConditionsCode(createUserRequest.userUsageConditionsCode));

        return new CreateUserResult();
    }

    @Override
    public GetUserResult getUser(GetUserRequest getUserRequest) {
        Preconditions.checkNotNull(getUserRequest);
        Preconditions.checkState(!Strings.isNullOrEmpty(getUserRequest.nickname));

        org.haiku.haikudepotserver.api2.model.GetUserResult resultV2
                = userApiService.getUser(new GetUserRequestEnvelope().nickname(getUserRequest.nickname));

        GetUserResult result = new GetUserResult();
        result.nickname = resultV2.getNickname();
        result.email = resultV2.getEmail();
        result.isRoot = resultV2.getIsRoot();
        result.active = resultV2.getActive();
        result.naturalLanguageCode = resultV2.getNaturalLanguageCode();
        result.createTimestamp = resultV2.getCreateTimestamp();
        result.modifyTimestamp = resultV2.getModifyTimestamp();
        result.lastAuthenticationTimestamp = resultV2.getLastAuthenticationTimestamp();

        GetUserResultUserUsageConditionsAgreement uuca = resultV2.getUserUsageConditionsAgreement();

        if (null != uuca) {
            result.userUsageConditionsAgreement = new GetUserResult.UserUsageConditionsAgreement();
            result.userUsageConditionsAgreement.timestampAgreed = uuca.getTimestampAgreed();
            result.userUsageConditionsAgreement.userUsageConditionsCode = uuca.getUserUsageConditionsCode();
            result.userUsageConditionsAgreement.isLatest = uuca.getIsLatest();
        }

        return result;
    }

    // TODO; some sort of brute-force checking here; too many authentication requests in a short period; go into lock-down?

    @Override
    public AuthenticateUserResult authenticateUser(AuthenticateUserRequest authenticateUserRequest) {
        Preconditions.checkNotNull(authenticateUserRequest);

        org.haiku.haikudepotserver.api2.model.AuthenticateUserResult resultV2
                = userApiService.authenticateUser(new AuthenticateUserRequestEnvelope()
                .nickname(authenticateUserRequest.nickname)
                .passwordClear(authenticateUserRequest.passwordClear));

        AuthenticateUserResult authenticateUserResult = new AuthenticateUserResult();
        authenticateUserResult.token = resultV2.getToken();
        return authenticateUserResult;
    }

    @Override
    public GetUserUsageConditionsResult getUserUsageConditions(GetUserUsageConditionsRequest request) {
        Preconditions.checkNotNull(request);

        org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsResult resultV2
                = userApiService.getUserUsageConditions(new GetUserUsageConditionsRequestEnvelope().code(request.code));

        GetUserUsageConditionsResult result = new GetUserUsageConditionsResult();
        result.code = resultV2.getCode();
        result.minimumAge = resultV2.getMinimumAge();
        return result;
    }

    @Override
    public AgreeUserUsageConditionsResult agreeUserUsageConditions(AgreeUserUsageConditionsRequest request) {
        Preconditions.checkNotNull(request);
        Preconditions.checkArgument(StringUtils.isNotBlank(request.nickname));
        Preconditions.checkArgument(StringUtils.isNotBlank(request.userUsageConditionsCode));

        userApiService.agreeUserUsageConditions(new AgreeUserUsageConditionsRequestEnvelope()
                .nickname(request.nickname)
                .userUsageConditionsCode(request.userUsageConditionsCode));

        return new AgreeUserUsageConditionsResult();
    }


}
