/*
 * Copyright 2019-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.user.controller;

import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;

@ContextConfiguration(classes = {TestAppConfig.class, TestServletConfig.class})
@WebAppConfiguration
public class UserControllerIT extends AbstractIntegrationTest {

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
