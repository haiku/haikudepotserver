package org.haiku.haikudepotserver.repository.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

class RepositoryControllerTest {

    private RequestMatcher requestMatcher = new RepositoryController.ImportRequestMatcher();

    @Test
    public void testMatchImportRequest() {
        HttpServletRequest request = new MockHttpServletRequest(null, null, "/__repository/xyz/import");

        // ------------------------------------
        boolean result = requestMatcher.matches(request);
        // ------------------------------------

        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void testNotMatchImportRequest() {
        HttpServletRequest request = new MockHttpServletRequest(null, null, "/__repository/all-en.json.gz");

        // ------------------------------------
        boolean result = requestMatcher.matches(request);
        // ------------------------------------

        Assertions.assertThat(result).isFalse();
    }

}