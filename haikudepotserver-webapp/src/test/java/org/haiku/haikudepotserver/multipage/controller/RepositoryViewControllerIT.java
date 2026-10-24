package org.haiku.haikudepotserver.multipage.controller;

import jakarta.annotation.Resource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractApplicationIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.util.UriComponentsBuilder;

public class RepositoryViewControllerIT extends AbstractApplicationIntegrationTest {

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
                    .getElementsByTagName("tr").get(1)
                    .getElementsByTagName("td").get(1);
            Assertions.assertThat(codeTdEl.getTextContent()).matches("^\\s+testreposrc_xyz_x86_gcc2\\s+$");
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
