package org.haiku.haikudepotserver.multipage.controller;

import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
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
public class RepositoryViewControllerIT extends AbstractIntegrationTest {

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
    public void testRepositoryView_html() throws Exception {
        IntegrationTestSupportService.StandardTestData testData = integrationTestSupportService.createStandardTestData();
        String url = UriComponentsBuilder.fromUriString("http://localhost/__multipage/repository/testrepo")
                .build()
                .toUriString();

        // ------------------------------------
        HtmlPage page = webClient.getPage(url);
        // ------------------------------------

        // check the table of repository sources
        {
            DomElement tableEl = page.getElementById("repository-view-repository-source-table");
            DomElement codeTdEl = tableEl.getElementsByTagName("tbody").getFirst()
                    .getElementsByTagName("tr").getFirst()
                    .getElementsByTagName("td").get(1);
            Assertions.assertThat(codeTdEl.getTextContent()).matches("^testreposrc_xyz_x86_gcc2$");
        }

        // check some of the code labels at the top
        {
            DomElement mainEl = page.getElementById("common-main-content-container");
            DomElement ddEl = mainEl.getElementsByTagName("dl").getFirst()
                    .getElementsByTagName("dd").get(1);
            Assertions.assertThat(ddEl.getTextContent()).matches("^Test Repository$");
        }
    }

}
