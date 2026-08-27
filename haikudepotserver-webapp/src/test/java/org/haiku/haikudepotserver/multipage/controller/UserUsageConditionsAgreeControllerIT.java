/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import jakarta.annotation.Resource;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.api2.UserApiService;
import org.haiku.haikudepotserver.config.TestAppConfig;
import org.haiku.haikudepotserver.config.TestServletConfig;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditionsAgreement;
import org.htmlunit.WebClient;
import org.htmlunit.html.DomElement;
import org.htmlunit.html.DomNodeList;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.htmlunit.MockMvcWebClientBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;

@WebAppConfiguration
@ContextConfiguration(classes = {TestAppConfig.class, TestServletConfig.class})
public class UserUsageConditionsAgreeControllerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private WebApplicationContext webApplicationContext;

    private MockMvc mockMvc;

    private WebClient webClient;
    @Autowired
    private UserApiService userApiService;

    @BeforeEach
    public void setUp() {
        webClient = MockMvcWebClientBuilder.webAppContextSetup(webApplicationContext).build();
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    }

    /**
     * <p>The root user should not need to agree to the user usage conditions so it should redirect them straight
     * to the final destination.</p>
     */
    @Test
    public void testRootDoesNotNeedToAgree() throws Exception {
        integrationTestSupportService.createStandardTestData();

        setAuthenticatedUserToRoot();

        // ------------------------------------
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/__multipage/user_usage_conditions_agree")
                        .queryParam("redirect_uri", "/some/other/path")
                        .locale(Locale.ENGLISH)
                )
                .andExpect(MockMvcResultMatchers.header().string("Location", "/some/other/path"))
                .andExpect(MockMvcResultMatchers.status().isFound());
        // ------------------------------------
    }

    @Test
    public void testUserAlreadyAgreedDoesNotNeedToAgree() throws Exception {
        ObjectContext context = serverRuntime.newContext();
        User user = integrationTestSupportService.createBasicUser(context, "agreed", "wgdgirjewwer");
        integrationTestSupportService.agreeToUserUsageConditions(context, user);

        setAuthenticatedUser("agreed");

        // ------------------------------------
        mockMvc.perform(MockMvcRequestBuilders
                        .get("/__multipage/user_usage_conditions_agree")
                        .queryParam("redirect_uri", "/some/other/path")
                        .locale(Locale.ENGLISH)
                )
                .andExpect(MockMvcResultMatchers.header().string("Location", "/some/other/path"))
                .andExpect(MockMvcResultMatchers.status().isFound());
        // ------------------------------------
    }

    @Test
    public void testUserAgrees() throws Exception {

        String agreementCode;

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "notagreed", "wgdgirjewwer");

            setAuthenticatedUser("notagreed");

            agreementCode = UserUsageConditions.getLatest(context).getCode();
        }

        // ------------------------------------
        mockMvc.perform(MockMvcRequestBuilders
                        .post("/__multipage/user_usage_conditions_agree")
                        .formField("code", agreementCode)
                        .formField("action", "AGREE")
                        .formField("redirectUri", "/red/green/blue")
                        .formField("isAgreed", "true")
                        .formField("isAgreedMinimumAge", "true")
                        .locale(Locale.ENGLISH)
                )
                .andExpect(MockMvcResultMatchers.header().string("Location", "/red/green/blue"))
                .andExpect(MockMvcResultMatchers.status().isFound());
        // ------------------------------------

        {
            ObjectContext context = serverRuntime.newContext();
            User userAfter = User.getByNickname(context, "notagreed");
            Assertions.assertThat(
                    userAfter.tryGetUserUsageConditionsAgreement()
                            .map(UserUsageConditionsAgreement::getUserUsageConditions)
                            .map(UserUsageConditions::getCode)
                            .orElse(null))
                    .isEqualTo(agreementCode);
        }
    }

    @Test
    public void testUserWithoutAgreementRendersForm() throws IOException {
        ObjectContext context = serverRuntime.newContext();
        integrationTestSupportService.createBasicUser(context, "agreed", "wgdgirjewwer");
        // ^ this user won't have the UUC agreed.

        setAuthenticatedUser("agreed");

        // ------------------------------------
        HtmlPage page = webClient.getPage("http://localhost/__multipage/user_usage_conditions_agree");
        // ------------------------------------

        {
            DomNodeList<DomElement> inputEls = page.getElementsByTagName("label");

            DomElement isAgreedMinimumAgeEl = inputEls.stream()
                    .filter(el -> Objects.equals(el.getAttribute("for"), "isAgreedMinimumAge"))
                    .findFirst()
                    .orElse(null);

            Assertions.assertThat(isAgreedMinimumAgeEl).isNotNull();
            Assertions.assertThat(isAgreedMinimumAgeEl.getTextContent()).isEqualTo("I am 16 or more years old");
        }
    }

}
