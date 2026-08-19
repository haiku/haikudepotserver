/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.hamcrest.Matchers;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Locale;

@WebAppConfiguration
@ContextConfiguration(classes = {TestAppConfig.class, TestServletConfig.class})
public class AboutControllerIT extends AbstractIntegrationTest {

    @Resource
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private WebClient webClient;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        webClient = MockMvcWebClientBuilder.webAppContextSetup(webApplicationContext).build();
    }

    @Test
    public void testAboutController() throws Exception {
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/__multipage/about")
                        .locale(Locale.ENGLISH)
                )
                .andExpect(MockMvcResultMatchers.content().contentType("text/html;charset=UTF-8"))
                .andExpect(MockMvcResultMatchers.content().string(
                        Matchers.containsString("Andrew Lindesay")
                ));
    }

    @Test
    public void testAboutController_html() throws Exception {

        // ------------------------------------
        HtmlPage page = webClient.getPage("http://localhost/__multipage/about");
        // ------------------------------------

        // check the contributors' table
        DomElement contributorsTableEl = page.getElementById("about-contributors-table");
        HtmlElement tBodyEl = contributorsTableEl.getElementsByTagName("tbody").getFirst();
        DomNodeList<HtmlElement> tdEls = tBodyEl.getElementsByTagName("tr").getFirst().getElementsByTagName("td");
        String td0Text = tdEls.get(0).getTextContent();
        String td1Text = tdEls.get(1).getTextContent();
        Assertions.assertThat(td0Text).matches("^\\s*Engineering\\s*$");
        Assertions.assertThat(td1Text).matches("^\\s*Andrew Lindesay\\s*$");

        // check the project version
        DomElement projectVersionEl = page.getElementById("about-project-version");
        Assertions.assertThat(projectVersionEl.getTextContent()).matches("^\\s*Version 1.0.\\d+.+$");
    }

}
