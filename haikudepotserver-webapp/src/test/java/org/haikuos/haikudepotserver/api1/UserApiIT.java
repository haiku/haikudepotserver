/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1;

import com.google.common.base.Optional;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.api1.model.user.*;
import org.haikuos.haikudepotserver.api1.support.AbstractSearchRequest;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.captcha.CaptchaService;
import org.haikuos.haikudepotserver.captcha.model.Captcha;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haikuos.haikudepotserver.passwordreset.PasswordResetException;
import org.haikuos.haikudepotserver.passwordreset.PasswordResetOrchestrationService;
import org.junit.Test;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.List;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class UserApiIT extends AbstractIntegrationTest {

    @Resource
    UserApi userApi;

    @Resource
    CaptchaService captchaService;

    @Resource
    PasswordResetOrchestrationService passwordResetOrchestrationService;

    @Test
    public void testUpdateUser() throws Exception {

        {
            ObjectContext context = serverRuntime.getContext();
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
            ObjectContext context = serverRuntime.getContext();
            Optional<User> user = User.getByNickname(context, "testuser");
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

        // ------------------------------------
        CreateUserResult result = userApi.createUser(request);
        // ------------------------------------

        ObjectContext context = serverRuntime.getContext();
        Optional<User> userOptional = User.getByNickname(context, "testuser");

        Assertions.assertThat(userOptional.isPresent()).isTrue();
        Assertions.assertThat(userOptional.get().getActive()).isTrue();
        Assertions.assertThat(userOptional.get().getIsRoot()).isFalse();
        Assertions.assertThat(userOptional.get().getNickname()).isEqualTo("testuser");
        Assertions.assertThat(userOptional.get().getNaturalLanguage().getCode()).isEqualTo("en");

        Assertions.assertThat(authenticationService.authenticateByNicknameAndPassword("testuser", "Ue4nI92Rw").get()).isEqualTo(userOptional.get().getObjectId());
    }

    @Test
    public void testGetUser_found() throws ObjectNotFoundException {

        ObjectContext context = serverRuntime.getContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009");
        setAuthenticatedUser("testuser");

        // ------------------------------------
        GetUserResult result = userApi.getUser(new GetUserRequest("testuser"));
        // ------------------------------------

        Assertions.assertThat(result.nickname).isEqualTo("testuser");
        // more to come here in time

    }

    @Test
    public void testAuthenticateUser_succcess() {

        ObjectContext context = serverRuntime.getContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // ------------------------------------
        AuthenticateUserResult result = userApi.authenticateUser(new AuthenticateUserRequest("testuser", "U7vqpsu6BB"));
        // ------------------------------------

        Assertions.assertThat(result.token).isNotNull();
        Assertions.assertThat(authenticationService.authenticateByToken(result.token).isPresent()).isTrue();

    }

    @Test
    public void testAuthenticateUser_fail() {

        ObjectContext context = serverRuntime.getContext();
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
            ObjectContext context = serverRuntime.getContext();
            User user = integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
            userOid = user.getObjectId();
            token = authenticationService.generateToken(user);
        }

        RenewTokenRequest renewTokenRequest = new RenewTokenRequest();
        renewTokenRequest.token = token;

        // ------------------------------------
        RenewTokenResult result = userApi.renewToken(renewTokenRequest);
        // ------------------------------------

        {
            Optional<ObjectId> afterUserObjectId = authenticationService.authenticateByToken(result.token);
            Assertions.assertThat(userOid).isEqualTo(afterUserObjectId.get());
        }

    }

    @Test
    public void testChangePassword() throws ObjectNotFoundException {

        Captcha captcha = captchaService.generate();
        ObjectContext context = serverRuntime.getContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // check that the password is correctly configured.
        Assertions.assertThat(authenticationService.authenticateByNicknameAndPassword("testuser", "U7vqpsu6BB").get()).isEqualTo(user.getObjectId());

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
        Assertions.assertThat(authenticationService.authenticateByNicknameAndPassword("testuser", "U7vqpsu6BB").isPresent()).isFalse();
        Assertions.assertThat(authenticationService.authenticateByNicknameAndPassword("testuser", "8R3nlp11gX").get()).isEqualTo(user.getObjectId());

    }

    @Test
    public void testSearchUsers() {

        setAuthenticatedUserToRoot();

        {
            ObjectContext context = serverRuntime.getContext();
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
        ObjectContext context = serverRuntime.getContext();
        User user = integrationTestSupportService.createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
        user.setEmail("integration-test-recipient@haiku-os.org");
        context.commitChanges();
    }

    private String getOnlyPasswordResetTokenCodeForTestUser() {
        ObjectContext context = serverRuntime.getContext();
        List<UserPasswordResetToken> tokens = UserPasswordResetToken.findByUser(
                context,
                User.getByNickname(context, "testuser").get());

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
            ObjectContext context = serverRuntime.getContext();
            User user = User.getByNickname(context, "testuser").get();

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
            passwordResetOrchestrationService.initiate("integration-test-recipient@haiku-os.org");
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
        Assertions.assertThat(authenticationService.authenticateByNicknameAndPassword("testuser", "kQ83hWi3oWnYY21k").isPresent()).isTrue();

        {
            ObjectContext context = serverRuntime.getContext();
            Optional<UserPasswordResetToken> token = UserPasswordResetToken.getByCode(context, request.token);
            Assertions.assertThat(token.isPresent()).isFalse();
        }

    }

}
