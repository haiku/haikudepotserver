package org.haiku.haikudepotserver.multipage.controller;

import jakarta.annotation.Resource;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.hamcrest.Matchers;
import org.htmlunit.WebClient;
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
public class BannerSearchControllerIT extends AbstractIntegrationTest {

    @Resource
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    @BeforeEach
    public void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /**
     * <p>This is the case where a search term is provided which hits one specific package.</p>
     */
    @Test
    public void testPkgHit() throws Exception {
        integrationTestSupportService.createStandardTestData();
        String expectedLocation = "/__multipage/pkg/pkg1?reposrc=testreposrc_xyz&arch=x86_64&vmajor=1&vmicro=2&vrev=4";

        // ------------------------------------
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/__multipage/banner-search")
                        .queryParam("srchexpr", "pkg1")
                        .locale(Locale.ENGLISH)
                )
                .andExpect(MockMvcResultMatchers.header().string("Location", expectedLocation))
                .andExpect(MockMvcResultMatchers.status().isFound());
        // ------------------------------------

    }

    /**
     * <p>This is the case where a search term is provided which hits no package.</p>
     */
    @Test
    public void testPkgMiss() throws Exception {
        integrationTestSupportService.createStandardTestData();
        String expectedLocation = "/__multipage/pkg?srchexpr=bananas";

        // ------------------------------------
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/__multipage/banner-search")
                        .queryParam("srchexpr", "bananas")
                        .locale(Locale.ENGLISH)
                )
                .andExpect(MockMvcResultMatchers.header().string("Location", expectedLocation))
                .andExpect(MockMvcResultMatchers.status().isFound());
        // ------------------------------------

    }

}
