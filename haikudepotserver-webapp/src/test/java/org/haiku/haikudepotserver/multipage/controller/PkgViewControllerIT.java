/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

@WebAppConfiguration
@ContextConfiguration(classes = {TestAppConfig.class, TestServletConfig.class})
public class PkgViewControllerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private WebApplicationContext webApplicationContext;

    private WebClient webClient;

    @BeforeEach
    public void setUp() {
        webClient = MockMvcWebClientBuilder.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void testPkgView_html() throws Exception {
        IntegrationTestSupportService.StandardTestData testData = integrationTestSupportService.createStandardTestData();
        String url = UriComponentsBuilder.fromUriString("http://localhost/__multipage/pkg")
                .pathSegment(testData.pkg1.getName())
                .queryParam("reposrc", testData.pkg1Version2x86_64.getRepositorySource().getCode())
                .queryParam("arch", testData.pkg1Version2x86_64.getArchitecture().getCode())
                .queryParam("vmajor", testData.pkg1Version2x86_64.getMajor())
                .queryParam("vminor", testData.pkg1Version2x86_64.getMinor())
                .queryParam("vmicro", testData.pkg1Version2x86_64.getMicro())
                .queryParam("vprel", testData.pkg1Version2x86_64.getPreRelease())
                .queryParam("vrev", testData.pkg1Version2x86_64.getRevision())
                .build()
                .toUriString();

        // ------------------------------------
        HtmlPage page = webClient.getPage(url);
        // ------------------------------------

        // check the contributors' table
        {
            DomElement detailsEl = page.getElementById("pkg-view-banner-container-details");
            HtmlElement h3El = detailsEl.getElementsByTagName("h3").getFirst();
            Assertions.assertThat(h3El.getTextContent()).matches("^\\s*Package 1\\s*$");
        }

        // check the description
        {
            DomElement descriptionEl = page.getElementById("pkg-view-description");
            Assertions.assertThat(descriptionEl.getTextContent().trim()).contains("pkg1Version2DescriptionEnglish_rockmelon");
        }
    }

}
