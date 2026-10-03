package org.haiku.haikudepotserver.support.web;

import org.fest.assertions.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

public class PrefixPathRequestMatcherTest {

    @Test
    public void testConstructorWithBadPath() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> {
                    new PrefixPathRequestMatcher("bad"); // missing leading slash.
                });
    }

    @Test
    public void testMatchesExact() {
        PrefixPathRequestMatcher matcher = new PrefixPathRequestMatcher("/brick");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/brick");

        // ------------------------------------
        boolean result = matcher.matches(request);
        // ------------------------------------

        Assertions.assertThat(result).isTrue();
    }


    @Test
    public void testMatches() {
        PrefixPathRequestMatcher matcher = new PrefixPathRequestMatcher("/brick");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/brickworks");

        // ------------------------------------
        boolean result = matcher.matches(request);
        // ------------------------------------

        Assertions.assertThat(result).isTrue();
    }

    @Test
    public void testNotMatches() {
        PrefixPathRequestMatcher matcher = new PrefixPathRequestMatcher("/brick");
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/br1ckworks"); // note the 1

        // ------------------------------------
        boolean result = matcher.matches(request);
        // ------------------------------------

        Assertions.assertThat(result).isFalse();
    }


}
