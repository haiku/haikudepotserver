/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.web;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class ExactPathRequestMatcherTest {

    @Test
    public void testConstructorWithBadPath() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new ExactPathRequestMatcher("   bad path   ");
                });
    }

    @Test
    public void testMatches() {
        ExactPathRequestMatcher matcher = new ExactPathRequestMatcher("/brick");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/brick");

        // ------------------------------------
        boolean result = matcher.matches(request);
        // ------------------------------------

        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void testNotMatches() {
        ExactPathRequestMatcher matcher = new ExactPathRequestMatcher("/brick");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/clay");

        // ------------------------------------
        boolean result = matcher.matches(request);
        // ------------------------------------

        Assertions.assertThat(result).isFalse();
    }

}
