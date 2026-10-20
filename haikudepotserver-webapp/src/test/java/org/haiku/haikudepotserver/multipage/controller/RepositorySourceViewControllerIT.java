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
public class RepositorySourceViewControllerIT extends AbstractIntegrationTest {

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
    public void testRepositorySourceView_html() throws Exception {
        IntegrationTestSupportService.StandardTestData testData = integrationTestSupportService.createStandardTestData();
        String url = UriComponentsBuilder.fromUriString("http://localhost/__multipage/repository_source/testreposrc_xyz")
                .build()
                .toUriString();

        // ------------------------------------
        HtmlPage page = webClient.getPage(url);
        // ------------------------------------

        // check the table of repository source mirrors
        {
            DomElement tableEl = page.getElementById("repository-source-view-repository-source-mirror-table");
            DomElement codeTdEl = tableEl.getElementsByTagName("tbody").getFirst()
                    .getElementsByTagName("tr").getFirst()
                    .getElementsByTagName("td").get(2);
            Assertions.assertThat(codeTdEl.getTextContent()).matches("^\\s+NZ\\s+$");
        }

        // check some of the code labels at the top
        {
            DomElement mainEl = page.getElementById("common-main-content-container");
            DomElement ddEl = mainEl.getElementsByTagName("dl").getFirst()
                    .getElementsByTagName("dd").getFirst();
            Assertions.assertThat(ddEl.getTextContent()).matches("^testreposrc_xyz$");
        }
    }

}
