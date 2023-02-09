/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api2;

import com.nimbusds.jwt.SignedJWT;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api2.model.AgreeUserUsageConditionsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.AuthenticateUserResult;
import org.haiku.haikudepotserver.api2.model.ChangePasswordRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CompletePasswordResetRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.CreateUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserResult;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetUserUsageConditionsResult;
import org.haiku.haikudepotserver.api2.model.InitiatePasswordResetRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RenewTokenRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.RenewTokenResult;
import org.haiku.haikudepotserver.api2.model.SearchUsersRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchUsersResult;
import org.haiku.haikudepotserver.api2.model.UpdateUserFilter;
import org.haiku.haikudepotserver.api2.model.UpdateUserRequestEnvelope;
import org.haiku.haikudepotserver.captcha.model.Captcha;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haiku.haikudepotserver.passwordreset.PasswordResetException;
import org.haiku.haikudepotserver.passwordreset.PasswordResetServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class UserApiServiceIT extends AbstractIntegrationTest {

    @Resource
    private UserApiService userApiService;

    @Resource
    private CaptchaService captchaService;

    @Resource
    private PasswordResetServiceImpl passwordResetService;

    @Test
    public void testUpdateUser() {

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
            setAuthenticatedUser("testuser");
        }

        UpdateUserRequestEnvelope request = new UpdateUserRequestEnvelope()
                .nickname("testuser")
                .naturalLanguageCode("de")
                .filter(List.of(UpdateUserFilter.NATURALLANGUAGE));

        // ------------------------------------
        userApiService.updateUser(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            User user = User.getByNickname(context, "testuser");
            Assertions.assertThat(user.getNaturalLanguage().getCode()).isEqualTo(NaturalLanguage.CODE_GERMAN);
        }

    }

    @Test
    public void testCreateUser() {

        Captcha captcha = captchaService.generate();

        CreateUserRequestEnvelope request = new CreateUserRequestEnvelope()
                .captchaToken(captcha.getToken())
                .captchaResponse(captcha.getResponse())
                .nickname("testuser")
                .passwordClear("Ue4nI92Rw")
                .naturalLanguageCode("en")
                .userUsageConditionsCode("UUC2021V01");

        // ------------------------------------
        userApiService.createUser(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.newContext();
        User user = User.getByNickname(context, "testuser");

        Assertions.assertThat(user.getActive()).isTrue();
        Assertions.assertThat(user.getIsRoot()).isFalse();
        Assertions.assertThat(user.getNickname()).isEqualTo("testuser");
        Assertions.assertThat(user.getNaturalLanguage().getCode()).isEqualTo("en");
        Assertions.assertThat(user.getLastAuthenticationTimestamp()).isNull();
        Assertions.assertThat(user.tryGetUserUsageConditionsAgreement().get().getUserUsageConditions().getCode())
                .isEqualTo("UUC2021V01");

        Assertions
                .assertThat(userAuthenticationService.authenticateByNicknameAndPassword("testuser", "Ue4nI92Rw").get())
                .isEqualTo(user.getObjectId());
    }

    @Test
    public void testGetUser_found() {

        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009");
        setAuthenticatedUser("testuser");

        GetUserRequestEnvelope request = new GetUserRequestEnvelope()
                .nickname("testuser");

        // ------------------------------------
        GetUserResult result = userApiService.getUser(request);
        // ------------------------------------

        Assertions.assertThat(result.getNickname()).isEqualTo("testuser");
        Assertions.assertThat(result.getLastAuthenticationTimestamp()).isNull();
        Assertions.assertThat(result.getCreateTimestamp()).isNotNull();
        Assertions.assertThat(result.getUserUsageConditionsAgreement()).isNull();
        // more to come here in time
    }

    @Test
    public void testGetUser_foundWithUserUsageConditionsAgreement() {

        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009");
        integrationTestSupportService.agreeToUserUsageConditions(context, user);
        setAuthenticatedUser("testuser");

        GetUserRequestEnvelope request = new GetUserRequestEnvelope()
                .nickname("testuser");

        // ------------------------------------
        GetUserResult result = userApiService.getUser(request);
        // ------------------------------------

        // just check the few things that come with the additional user usage agreement
        Assertions.assertThat(result.getUserUsageConditionsAgreement().getTimestampAgreed()).isNotNull();
        Assertions.assertThat(result.getUserUsageConditionsAgreement().getUserUsageConditionsCode()).isEqualTo("UUC2021V01");
    }

    @Test
    public void testAuthenticateUser_succcessNoAgreement() throws Exception {

        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        AuthenticateUserRequestEnvelope request = new AuthenticateUserRequestEnvelope()
                .nickname("testuser")
                .passwordClear("U7vqpsu6BB");

        // ------------------------------------
        AuthenticateUserResult result = userApiService.authenticateUser(request);
        // ------------------------------------

        Assertions.assertThat(result.getToken()).isNotNull();
        Assertions.assertThat(userAuthenticationService.authenticateByToken(result.getToken()).isPresent()).isTrue();

        SignedJWT signedJWT = SignedJWT.parse(result.getToken());
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

        AuthenticateUserRequestEnvelope request = new AuthenticateUserRequestEnvelope()
                .nickname("testuser")
                .passwordClear("U7vqpsu6BB");

        // ------------------------------------
        AuthenticateUserResult result = userApiService.authenticateUser(request);
        // ------------------------------------

        Assertions.assertThat(result.getToken()).isNotNull();
        Assertions.assertThat(userAuthenticationService.authenticateByToken(result.getToken()).isPresent()).isTrue();

        SignedJWT signedJWT = SignedJWT.parse(result.getToken());
        Map<String, Object> claims = signedJWT.getJWTClaimsSet().getClaims();

        Assertions.assertThat(signedJWT.getJWTClaimsSet().getSubject()).isEqualTo("testuser@hds");

        // because the user has agreed to the usage conditions they will not get
        // this flag in the response JWT token.
        Assertions.assertThat(claims.get("ucnd")).isNull();
    }

    @Test
    public void testAuthenticateUser_fail() {

        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        AuthenticateUserRequestEnvelope request = new AuthenticateUserRequestEnvelope()
                .nickname("testuser")
                .passwordClear("y63j20f22");

        // ------------------------------------
        AuthenticateUserResult result = userApiService.authenticateUser(request);
        // ------------------------------------

        Assertions.assertThat(result.getToken()).isNull();
    }

    @Test
    public void testRenewToken() {

        String token;
        ObjectId userOid;

        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
            userOid = user.getObjectId();
            token = userAuthenticationService.generateToken(user);
        }

        RenewTokenRequestEnvelope request = new RenewTokenRequestEnvelope()
                .token(token);

        // ------------------------------------
        RenewTokenResult result = userApiService.renewToken(request);
        // ------------------------------------

        {
            Optional<ObjectId> afterUserObjectId = userAuthenticationService.authenticateByToken(result.getToken());
            Assertions.assertThat(userOid).isEqualTo(afterUserObjectId.get());
        }

    }

    @Test
    public void testChangePassword() {

        Captcha captcha = captchaService.generate();
        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // check that the password is correctly configured.
        Assertions.assertThat(userAuthenticationService.authenticateByNicknameAndPassword("testuser", "U7vqpsu6BB").get()).isEqualTo(user.getObjectId());

        // now change it.
        ChangePasswordRequestEnvelope request = new ChangePasswordRequestEnvelope()
                .nickname("testuser")
                .captchaResponse(captcha.getResponse())
                .captchaToken(captcha.getToken())
                .newPasswordClear("8R3nlp11gX")
                .oldPasswordClear("U7vqpsu6BB");

        // ------------------------------------
        userApiService.changePassword(request);
        // ------------------------------------

        // now check that the old authentication no longer works and the new one does work
        Assertions.assertThat(
                userAuthenticationService.authenticateByNicknameAndPassword("testuser", "U7vqpsu6BB").isPresent())
                .isFalse();
        Assertions.assertThat(
                userAuthenticationService.authenticateByNicknameAndPassword("testuser", "8R3nlp11gX").get())
                .isEqualTo(user.getObjectId());

    }

    @Test
    public void testSearchUsers() {

        setAuthenticatedUserToRoot();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "onehunga", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "mangere", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "avondale", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "remuera", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "freemansbay", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "kohimarama", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "mtwellington", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "mtalbert", "U7vqpsu6BB");
            integrationTestSupportService.createBasicUser(context, "kingsland", "U7vqpsu6BB");
        }

        SearchUsersRequestEnvelope request = new SearchUsersRequestEnvelope()
                .limit(2)
                .offset(0)
                .includeInactive(true)
                .expression("er")
                .expressionType(SearchUsersRequestEnvelope.ExpressionTypeEnum.CONTAINS);

        // ------------------------------------
        SearchUsersResult result = userApiService.searchUsers(request);
        // ------------------------------------

        // we should select from mangere, remuera, mtalbert and only see
        // mangere, mtalbert in that order.

        Assertions.assertThat(result.getTotal()).isEqualTo(3);
        Assertions.assertThat(result.getItems().size()).isEqualTo(2);
        Assertions.assertThat(result.getItems().get(0).getNickname()).isEqualTo("mangere");
        Assertions.assertThat(result.getItems().get(0).getActive()).isEqualTo(Boolean.TRUE);
        Assertions.assertThat(result.getItems().get(1).getNickname()).isEqualTo("mtalbert");

    }

    private void createPasswordResetTestUser() {
        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
        user.setEmail("integration-test-recipient@example.com");
        context.commitChanges();
    }

    private String getOnlyPasswordResetTokenCodeForTestUser() {
        ObjectContext context = serverRuntime.newContext();
        List<UserPasswordResetToken> tokens = UserPasswordResetToken.findByUser(
                context,
                User.getByNickname(context, "testuser"));

        switch (tokens.size()) {
            case 0:
                return null;
            case 1:
                return tokens.get(0).getCode();

            default:
                throw new IllegalStateException("more than one password reset token for the user 'testuser'");
        }
    }

    /**
     * <p>This test will check the initiation of the password reset procedure.</p>
     */

    @Test
    public void testInitiatePasswordReset() {

        createPasswordResetTestUser();
        Captcha captcha = captchaService.generate();

        InitiatePasswordResetRequestEnvelope request = new InitiatePasswordResetRequestEnvelope()
                .captchaToken(captcha.getToken())
                .captchaResponse(captcha.getResponse())
                .email("integration-test-recipient@example.com");

        // ------------------------------------
        userApiService.initiatePasswordReset(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            User user = User.getByNickname(context, "testuser");

            // check for the presence of a token.
            List<UserPasswordResetToken> tokens = UserPasswordResetToken.findByUser(context, user);
            Assertions.assertThat(tokens.size()).isEqualTo(1);
            UserPasswordResetToken token = tokens.get(0);

            // check that an email did actually get sent.

            List<SimpleMailMessage> messages = mailSender.getSentMessages();
            Assertions.assertThat(messages.size()).isEqualTo(1);
            SimpleMailMessage message = messages.get(0);
            Assertions.assertThat(message.getTo()).isEqualTo(new String[]{"integration-test-recipient@example.com"});
            Assertions.assertThat(message.getFrom()).isEqualTo("integration-test-sender@example.com");
            Assertions.assertThat(message.getText()).contains(token.getCode());
        }

    }

    /**
     * <p>This checks a password reset token can be picked-up and actioned.  The token will have been sent to the
     * user earlier in an email.</p>
     */

    @Test
    public void testCompletePasswordReset_ok() {

        createPasswordResetTestUser();
        Assertions.assertThat(getOnlyPasswordResetTokenCodeForTestUser()).isNull();

        try {
            passwordResetService.initiate("integration-test-recipient@example.com");
        } catch (PasswordResetException pre) {
            throw new IllegalStateException("unable to initiate the password reset when testing complete", pre);
        }

        Captcha captcha = captchaService.generate();

        CompletePasswordResetRequestEnvelope request = new CompletePasswordResetRequestEnvelope()
                .captchaResponse(captcha.getResponse())
                .captchaToken(captcha.getToken())
                .token(getOnlyPasswordResetTokenCodeForTestUser())
                .passwordClear("kQ83hWi3oWnYY21k");

        // ------------------------------------
        userApiService.completePasswordReset(request);
        // ------------------------------------

        // the user should now be able to be authenticated with the new password.
        Assertions.assertThat(userAuthenticationService.authenticateByNicknameAndPassword("testuser", "kQ83hWi3oWnYY21k").isPresent()).isTrue();

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<UserPasswordResetToken> token = UserPasswordResetToken.getByCode(context, request.getToken());
            Assertions.assertThat(token.isPresent()).isFalse();
        }

    }

    @Test
    public void testGetUserUsageConditions() {
        GetUserUsageConditionsRequestEnvelope request = new GetUserUsageConditionsRequestEnvelope()
                .code("UUC2019V01");

        // ------------------------------------
        GetUserUsageConditionsResult result = userApiService.getUserUsageConditions(request);
        // ------------------------------------

        Assertions.assertThat(result.getCode()).isEqualTo("UUC2019V01");
        Assertions.assertThat(result.getMinimumAge()).isEqualTo(16);
    }

    @Test
    public void testAgreeUserUsageConditions() {
        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009");
        }

        setAuthenticatedUser("testuser");

        AgreeUserUsageConditionsRequestEnvelope request = new AgreeUserUsageConditionsRequestEnvelope()
                .userUsageConditionsCode("UUC2019V01")
                .nickname("testuser");

        // ------------------------------------
        userApiService.agreeUserUsageConditions(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            User userAfter = User.getByNickname(context, "testuser");
            Assertions.assertThat(userAfter.tryGetUserUsageConditionsAgreement().get().getUserUsageConditions().getCode())
                    .isEqualTo("UUC2019V01");
        }
    }


}
