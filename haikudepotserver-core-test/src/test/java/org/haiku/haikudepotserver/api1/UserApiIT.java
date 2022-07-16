/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.nimbusds.jwt.SignedJWT;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
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
import org.haiku.haikudepotserver.captcha.model.Captcha;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haiku.haikudepotserver.passwordreset.PasswordResetServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class UserApiIT extends AbstractIntegrationTest {

    @Resource
    private UserApi userApi;

    @Resource
    private CaptchaService captchaService;

    @Resource
    private PasswordResetServiceImpl passwordResetService;

    @Test
    public void testCreateUser() {

        Captcha captcha = captchaService.generate();
        CreateUserRequest request = new CreateUserRequest();
        request.captchaToken = captcha.getToken();
        request.captchaResponse = captcha.getResponse();
        request.nickname = "testuser";
        request.passwordClear = "Ue4nI92Rw";
        request.naturalLanguageCode = "en";
        request.userUsageConditionsCode = "UUC2021V01";

        // ------------------------------------
        CreateUserResult result = userApi.createUser(request);
        // ------------------------------------

        Assertions.assertThat(result).isNotNull();

        ObjectContext context = serverRuntime.newContext();
        Optional<User> userOptional = User.tryGetByNickname(context, "testuser");
        Assertions.assertThat(userOptional.isPresent()).isTrue();
        User user = userOptional.get();

        Assertions.assertThat(user.getActive()).isTrue();
        Assertions.assertThat(user.getIsRoot()).isFalse();
        Assertions.assertThat(user.getNickname()).isEqualTo("testuser");
        Assertions.assertThat(user.getNaturalLanguage().getCode()).isEqualTo("en");
        Assertions.assertThat(user.getLastAuthenticationTimestamp()).isNull();
        Assertions.assertThat(user.tryGetUserUsageConditionsAgreement().get().getUserUsageConditions().getCode())
                .isEqualTo("UUC2021V01");

        Assertions.assertThat(userAuthenticationService.authenticateByNicknameAndPassword("testuser", "Ue4nI92Rw").get()).isEqualTo(userOptional.get().getObjectId());
    }

    @Test
    public void testGetUser_found() {

        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009");
        setAuthenticatedUser("testuser");

        // ------------------------------------
        GetUserResult result = userApi.getUser(new GetUserRequest("testuser"));
        // ------------------------------------

        Assertions.assertThat(result.nickname).isEqualTo("testuser");
        Assertions.assertThat(result.lastAuthenticationTimestamp).isNull();
        Assertions.assertThat(result.createTimestamp).isNotNull();
        Assertions.assertThat(result.userUsageConditionsAgreement).isNull();
        // more to come here in time
    }

    @Test
    public void testGetUser_foundWithUserUsageConditionsAgreement() {

        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009");
        integrationTestSupportService.agreeToUserUsageConditions(context, user);
        setAuthenticatedUser("testuser");

        // ------------------------------------
        GetUserResult result = userApi.getUser(new GetUserRequest("testuser"));
        // ------------------------------------

        // just check the few things that come with the additional user usage agreement
        Assertions.assertThat(result.userUsageConditionsAgreement.timestampAgreed).isNotNull();
        Assertions.assertThat(result.userUsageConditionsAgreement.userUsageConditionsCode).isEqualTo("UUC2021V01");
    }

    @Test
    public void testAuthenticateUser_succcessNoAgreement() throws Exception {

        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // ------------------------------------
        AuthenticateUserResult result = userApi.authenticateUser(new AuthenticateUserRequest("testuser", "U7vqpsu6BB"));
        // ------------------------------------

        Assertions.assertThat(result.token).isNotNull();
        Assertions.assertThat(userAuthenticationService.authenticateByToken(result.token).isPresent()).isTrue();

        SignedJWT signedJWT = SignedJWT.parse(result.token);
        Map<String, Object> claims = signedJWT.getJWTClaimsSet().getClaims();

        Assertions.assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("testuser@hds");

        // because the user has not agreed to the usage conditions they will get
        // this flag come up in their token.
        Assertions.assertThat(claims.get("ucnd")).isEqualTo(Boolean.TRUE);

        {
            User userAfter = User.getByNickname(context, "testuser");
            Assertions.assertThat(userAfter.getLastAuthenticationTimestamp()).isNotNull();
        }

    }

    @Test
    public void testAuthenticateUser_succcessWithAgreement() throws Exception {

        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        integrationTestSupportService.agreeToUserUsageConditions(context, user);
        setAuthenticatedUser("testuser");

        // ------------------------------------
        AuthenticateUserResult result = userApi.authenticateUser(new AuthenticateUserRequest("testuser", "U7vqpsu6BB"));
        // ------------------------------------

        Assertions.assertThat(result.token).isNotNull();
        Assertions.assertThat(userAuthenticationService.authenticateByToken(result.token).isPresent()).isTrue();

        SignedJWT signedJWT = SignedJWT.parse(result.token);
        Map<String, Object> claims = signedJWT.getJWTClaimsSet().getClaims();

        Assertions.assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("testuser@hds");

        // because the user has agreed to the usage conditions they will not get
        // this flag in the response JWT token.
        Assertions.assertThat(claims.get("ucnd")).isNull();
    }

    @Test
    public void testAuthenticateUser_fail() {

        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // ------------------------------------
        AuthenticateUserResult result = userApi.authenticateUser(new AuthenticateUserRequest("testuser", "y63j20f22"));
        // ------------------------------------

        Assertions.assertThat(result.token).isNull();
    }

    private void createPasswordResetTestUser() {
        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
        user.setEmail("integration-test-recipient@haiku-os.org");
        context.commitChanges();
    }

    private String getOnlyPasswordResetTokenCodeForTestUser() {
        ObjectContext context = serverRuntime.newContext();
        List<UserPasswordResetToken> tokens = UserPasswordResetToken.findByUser(
                context,
                User.tryGetByNickname(context, "testuser").get());

        switch (tokens.size()) {
            case 0:
                return null;
            case 1:
                return tokens.get(0).getCode();

            default:
                throw new IllegalStateException("more than one password reset token for the user 'testuser'");
        }
    }

    @Test
    public void testGetUserUsageConditions() {
        GetUserUsageConditionsRequest request = new GetUserUsageConditionsRequest();
        request.code = "UUC2019V01";

        // ------------------------------------
        GetUserUsageConditionsResult result = userApi.getUserUsageConditions(request);
        // ------------------------------------

        Assertions.assertThat(result.code).isEqualTo("UUC2019V01");
        Assertions.assertThat(result.minimumAge).isEqualTo(16);
    }

    public void testAgreeUserUsageConditions() {
        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009");
        setAuthenticatedUser("testuser");

        AgreeUserUsageConditionsRequest request = new AgreeUserUsageConditionsRequest();
        request.userUsageConditionsCode = "UUC2019V01";
        request.nickname = "testuser";

        // ------------------------------------
        AgreeUserUsageConditionsResult result = userApi.agreeUserUsageConditions(request);
        // ------------------------------------

        Assertions.assertThat(result).isNotNull();

        {
            User userAfter = User.getByNickname(context, "testuser");
            Assertions.assertThat(userAfter.tryGetUserUsageConditionsAgreement().get().getUserUsageConditions().getCode())
                    .isEqualTo("UUC2019V01");
        }
    }


}
