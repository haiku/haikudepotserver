/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;

import java.io.IOException;
import java.util.Locale;

@ContextConfiguration(classes = {TestAppConfig.class, TestServletConfig.class})
@WebAppConfiguration
class PkgSearchControllerTest extends AbstractIntegrationTest {

    @Resource
    public PkgSearchController searchController;

    @Test
    public void testHandleOpenSearchDescription() throws IOException {

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        Locale locale = Locale.of("en", "GB", "");

        // ------------------------------------
        searchController.handleOpenSearchDescription(request, response, locale);
        // -----------------------------------

        Assertions.assertThat(response.getContentType()).isEqualTo("application/opensearchdescription+xml");

        String actualPayload = response.getContentAsString();
        Assertions.assertThat(actualPayload).contains("<Image type=\"image/svg+xml\">");
        // ^ only checks basic rendering worked.
    }

}