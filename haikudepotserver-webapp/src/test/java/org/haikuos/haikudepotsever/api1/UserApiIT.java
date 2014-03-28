/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.api1;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.api1.UserApi;
import org.haikuos.haikudepotserver.api1.model.user.*;
import org.haikuos.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haikuos.haikudepotserver.captcha.CaptchaService;
import org.haikuos.haikudepotserver.captcha.model.Captcha;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AuthenticationService;
import org.haikuos.haikudepotsever.api1.support.AbstractIntegrationTest;
import org.junit.Test;

import javax.annotation.Resource;
import java.util.Collections;

public class UserApiIT extends AbstractIntegrationTest {

    @Resource
    UserApi userApi;

    @Resource
    CaptchaService captchaService;

    @Resource
    AuthenticationService authenticationService;

    private User createBasicUser(ObjectContext context, String nickname, String password) {
        User user = context.newObject(User.class);
        user.setNickname(nickname);
        user.setPasswordSalt(); // random
        user.setPasswordHash(authenticationService.hashPassword(user, password));
        user.setNaturalLanguage(NaturalLanguage.getByCode(context, NaturalLanguage.CODE_ENGLISH).get());
        context.commitChanges();
        return user;
    }

    @Test
    public void testUpdateUser() throws Exception {

        {
            ObjectContext context = serverRuntime.getContext();
            User user = createBasicUser(context, "testuser", "yUe4o2Nwe009"); // language is english
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
        Optional<User> userOptional = User.getByNickname(context,"testuser");

        Assertions.assertThat(userOptional.isPresent()).isTrue();
        Assertions.assertThat(userOptional.get().getActive()).isTrue();
        Assertions.assertThat(userOptional.get().getIsRoot()).isFalse();
        Assertions.assertThat(userOptional.get().getNickname()).isEqualTo("testuser");
        Assertions.assertThat(userOptional.get().getNaturalLanguage().getCode()).isEqualTo("en");

        Assertions.assertThat(authenticationService.authenticate("testuser","Ue4nI92Rw").get()).isEqualTo(userOptional.get().getObjectId());
    }

    @Test
    public void testGetUser_found() throws ObjectNotFoundException {

        ObjectContext context = serverRuntime.getContext();
        User user = createBasicUser(context,"testuser","yUe4o2Nwe009");
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
        User user = createBasicUser(context, "testuser", "U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // ------------------------------------
        AuthenticateUserResult result = userApi.authenticateUser(new AuthenticateUserRequest("testuser","U7vqpsu6BB"));
        // ------------------------------------

        Assertions.assertThat(result.authenticated).isTrue();

    }

    @Test
    public void testAuthenticateUser_fail() {

        ObjectContext context = serverRuntime.getContext();
        User user = createBasicUser(context,"testuser","U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // ------------------------------------
        AuthenticateUserResult result = userApi.authenticateUser(new AuthenticateUserRequest("testuser","y63j20f22"));
        // ------------------------------------

        Assertions.assertThat(result.authenticated).isFalse();
    }

    @Test
    public void testChangePassword() throws ObjectNotFoundException {

        Captcha captcha = captchaService.generate();
        ObjectContext context = serverRuntime.getContext();
        User user = createBasicUser(context,"testuser","U7vqpsu6BB");
        setAuthenticatedUser("testuser");

        // check that the password is correctly configured.
        Assertions.assertThat(authenticationService.authenticate("testuser","U7vqpsu6BB").get()).isEqualTo(user.getObjectId());

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
        Assertions.assertThat(authenticationService.authenticate("testuser","U7vqpsu6BB").isPresent()).isFalse();
        Assertions.assertThat(authenticationService.authenticate("testuser","8R3nlp11gX").get()).isEqualTo(user.getObjectId());

    }

}
