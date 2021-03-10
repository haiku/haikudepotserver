/*
 * Copyright 2018-2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.nimbusds.jwt.SignedJWT;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.api1.model.user.*;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.passwordreset.PasswordResetException;
import org.haiku.haikudepotserver.api1.support.AbstractSearchRequest;
import org.haiku.haikudepotserver.captcha.model.CaptchaService;
import org.haiku.haikudepotserver.captcha.model.Captcha;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haiku.haikudepotserver.passwordreset.PasswordResetServiceImpl;
import org.junit.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
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
    public void testUpdateUser() throws Exception {

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
            setAuthenticatedUser("testuser");
        }

        UpdateUserRequest request = new UpdateUserRequest();
        request.nickname = "testuser";
        request.filter = Collections.singletonList(UpdateUserRequest.Filter.NATURALLANGUAGE);
        request.naturalLanguageCode = NaturalLanguage.CODE_GERMAN;

        // ------------------------------------
        userApi.updateUser(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<User> user = User.tryGetByNickname(context, "testuser");
            Assertions.assertThat(user.get().getNaturalLanguage().getCode()).isEqualTo(NaturalLanguage.CODE_GERMAN);
        }

    }

    @Test
    public void testCreateUser() throws Exception {

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

        RenewTokenRequest renewTokenRequest = new RenewTokenRequest();
        renewTokenRequest.token = token;

        // ------------------------------------
        RenewTokenResult result = userApi.renewToken(renewTokenRequest);
        // ------------------------------------

        {
            Optional<ObjectId> afterUserObjectId = userAuthenticationService.authenticateByToken(result.token);
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
        ChangePasswordRequest request = new ChangePasswordRequest();
        request.nickname = "testuser";
        request.captchaResponse = captcha.getResponse();
        request.captchaToken = captcha.getToken();
        request.newPasswordClear = "8R3nlp11gX";
        request.oldPasswordClear = "U7vqpsu6BB";

        // ------------------------------------
        userApi.changePassword(request);
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

        SearchUsersRequest request = new SearchUsersRequest();
        request.limit = 2;
        request.offset = 0;
        request.includeInactive = true;
        request.expression = "er";
        request.expressionType = AbstractSearchRequest.ExpressionType.CONTAINS;

        // ------------------------------------
        SearchUsersResult result = userApi.searchUsers(request);
        // ------------------------------------

        // we should select from mangere, remuera, mtalbert and only see
        // mangere, mtalbert in that order.

        Assertions.assertThat(result.total).isEqualTo(3);
        Assertions.assertThat(result.items.size()).isEqualTo(2);
        Assertions.assertThat(result.items.get(0).nickname).isEqualTo("mangere");
        Assertions.assertThat(result.items.get(0).active).isEqualTo(Boolean.TRUE);
        Assertions.assertThat(result.items.get(1).nickname).isEqualTo("mtalbert");

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

    /**
     * <p>This test will check the initiation of the password reset procedure.</p>
     */

    @Test
    public void testInitiatePasswordReset() {

        createPasswordResetTestUser();
        Captcha captcha = captchaService.generate();
        InitiatePasswordResetRequest request = new InitiatePasswordResetRequest();
        request.captchaToken = captcha.getToken();
        request.captchaResponse = captcha.getResponse();
        request.email = "integration-test-recipient@haiku-os.org";

        // ------------------------------------
        userApi.initiatePasswordReset(request);
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            User user = User.tryGetByNickname(context, "testuser").get();

            // check for the presence of a token.
            List<UserPasswordResetToken> tokens = UserPasswordResetToken.findByUser(context, user);
            Assertions.assertThat(tokens.size()).isEqualTo(1);
            UserPasswordResetToken token = tokens.get(0);

            // check that an email did actually get sent.

            List<SimpleMailMessage> messages = mailSender.getSentMessages();
            Assertions.assertThat(messages.size()).isEqualTo(1);
            SimpleMailMessage message = messages.get(0);
            Assertions.assertThat(message.getTo()).isEqualTo(new String[]{"integration-test-recipient@haiku-os.org"});
            Assertions.assertThat(message.getFrom()).isEqualTo("integration-test-sender@haiku-os.org");
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
            passwordResetService.initiate("integration-test-recipient@haiku-os.org");
        } catch (PasswordResetException pre) {
            throw new IllegalStateException("unable to initiate the password reset when testing complete", pre);
        }

        Captcha captcha = captchaService.generate();
        CompletePasswordResetRequest request = new CompletePasswordResetRequest();
        request.captchaToken = captcha.getToken();
        request.captchaResponse = captcha.getResponse();
        request.token = getOnlyPasswordResetTokenCodeForTestUser();
        request.passwordClear = "kQ83hWi3oWnYY21k";

        // ------------------------------------
        userApi.completePasswordReset(request);
        // ------------------------------------

        // the user should now be able to be authenticated with the new password.
        Assertions.assertThat(userAuthenticationService.authenticateByNicknameAndPassword("testuser", "kQ83hWi3oWnYY21k").isPresent()).isTrue();

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<UserPasswordResetToken> token = UserPasswordResetToken.getByCode(context, request.token);
            Assertions.assertThat(token.isPresent()).isFalse();
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
