/*
 * Copyright 2018, Andrew Lindesay
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
import org.haiku.haikudepotserver.security.model.AuthenticationService;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class AuthenticationServiceIT extends AbstractIntegrationTest {

    @Resource
    private AuthenticationService authenticationService;

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
        Optional<ObjectId> result = authenticationService
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
        user.setPasswordSalt("cad3422ea02761f8");
        String passwordHash = authenticationService.hashPassword(user,"p4mphl3t");
        Assertions.assertThat(passwordHash).isEqualTo("b9c4717bc5c6d16f2be9e967ab0c752f8ac2084f95781989f39cf8736e2edeef");
    }

    @Test
    public void testHashPassword_2() {
        User user = new User();
        user.setPasswordSalt("66a9b264bf730ac2");
        String passwordHash = authenticationService.hashPassword(user,"Pa55word0");
        Assertions.assertThat(passwordHash).isEqualTo("d439da8f2ec8c7aa3d0c9c2a1dd7cd6dcbf8b4435f9e288cc1a6f7b77d47361e");
    }

}
