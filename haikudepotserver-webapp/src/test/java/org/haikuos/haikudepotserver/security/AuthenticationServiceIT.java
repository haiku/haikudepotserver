/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security;

import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.junit.Test;

import javax.annotation.Resource;

public class AuthenticationServiceIT extends AbstractIntegrationTest {

    @Resource
    AuthenticationService authenticationService;

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
