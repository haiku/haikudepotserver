/*
 * Copyright 2018-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class UserAuthenticationServiceIT extends AbstractIntegrationTest {

    @Resource
    private UserAuthenticationService userAuthenticationService;

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Test
    public void testAuthenticateByNicknameAndPassword() {
        {
            ObjectContext context = serverRuntime.newContext();
            User user = integrationTestSupportService.createBasicUser(
                context, "frank", "FidgetSpinn3rs");
            Assertions.assertThat(user.getLastAuthenticationTimestamp()).isNull();
        }

        // ---------------------------------
        Optional<ObjectId> result = userAuthenticationService
                .authenticateByNicknameAndPassword(
                    "frank", "FidgetSpinn3rs");
        // ---------------------------------

        Assertions.assertThat(result.isPresent()).isTrue();

        {
            ObjectContext context = serverRuntime.newContext();
            User user = User.getByNickname(context, "frank");
            Assertions.assertThat(user.getLastAuthenticationTimestamp()).isNotNull();
        }
    }

    @Test
    public void testHashPassword() {
        User user = new User();

        // -----------------
        userAuthenticationService.setPassword(user, "p4mphl3t");
        // -----------------

        Assertions.assertThat(userAuthenticationService.matchPassword(user, "p4mphl3t")).isTrue();
        Assertions.assertThat(userAuthenticationService.matchPassword(user, "Other")).isFalse();
    }

    @Test
    public void testHashPassword_2() {
        User user = new User();

        // -----------------
        userAuthenticationService.setPassword(user, "Pa55word0");
        // -----------------

        Assertions.assertThat(userAuthenticationService.matchPassword(user, "Pa55word0")).isTrue();
        Assertions.assertThat(userAuthenticationService.matchPassword(user, "Other")).isFalse();
    }

    @Test(expected = Exception.class)
    public void testClearPassword() {
        User user = new User();

        // -----------------
        userAuthenticationService.setPassword(user, null);
        // -----------------

        // expecting an exception.
    }

}
