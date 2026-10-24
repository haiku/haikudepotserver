/*
 * Copyright 2019-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.user.controller;

import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractApplicationIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

public class UserControllerIT extends AbstractApplicationIntegrationTest {

    @Autowired
    public UserController userController;

    @Test
    public void testGetUserUsageConditionsHtml() throws Exception {

        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        userController.handleGetUserUsageConditionsHtml(
                response,
                "UUC2019V01");
        // ------------------------------------

        Assertions.assertThat(response.getStatus()).isEqualTo(200);
        String responseString = response.getContentAsString();

        Assertions.assertThat(responseString).contains("<h4>Tracking</h4>");
    }

    @Test
    public void testGetUserUsageConditionsMarkdown() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        // ------------------------------------
        userController.handleGetUserUsageConditionsMarkdown(
                response,
                "UUC2019V01");
        // ------------------------------------

        Assertions.assertThat(response.getStatus()).isEqualTo(200);
        String responseString = response.getContentAsString();

        Assertions.assertThat(responseString).contains("#### Tracking");
    }

}
