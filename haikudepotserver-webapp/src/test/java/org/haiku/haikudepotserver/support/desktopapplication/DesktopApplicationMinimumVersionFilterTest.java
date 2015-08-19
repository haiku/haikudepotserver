/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.desktopapplication;

import com.google.common.net.HttpHeaders;
import org.haiku.haikudepotserver.MockServlet;
import org.junit.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import static org.fest.assertions.Assertions.assertThat;

public class DesktopApplicationMinimumVersionFilterTest {

    private void checkFilter_invokesServlet(HttpServletRequest request, String minimumVersionString) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockServlet mockServlet = new MockServlet();
        MockFilterChain filterChain = new MockFilterChain(mockServlet);

        DesktopApplicationMinimumVersionFilter filter = new DesktopApplicationMinimumVersionFilter();
        filter.setMinimumVersionString(minimumVersionString);
        filter.init();

        // --------------------------
        filter.doFilter(request, response, filterChain);
        // --------------------------

        assertThat(mockServlet.wasInvoked()).isTrue();
        assertThat(response.getStatus()).isNotEqualTo(HttpServletResponse.SC_PRECONDITION_FAILED);
        assertThat(response.getHeader(DesktopApplicationMinimumVersionFilter.HEADER_MINIMUM_VERSION)).isNull();
    }

    private void checkFilter_fails(HttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockServlet mockServlet = new MockServlet();
        MockFilterChain filterChain = new MockFilterChain(mockServlet);

        DesktopApplicationMinimumVersionFilter filter = new DesktopApplicationMinimumVersionFilter();
        filter.setMinimumVersionString("1.2.3");
        filter.init();

        // --------------------------
        filter.doFilter(request, response, filterChain);
        // --------------------------

        assertThat(mockServlet.wasInvoked()).isFalse();
        assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_PRECONDITION_FAILED);
        assertThat(response.getHeader(DesktopApplicationMinimumVersionFilter.HEADER_MINIMUM_VERSION)).isEqualTo("1.2.3");
    }

    /**
     * <p>Checks that if there is no user agent, that the filter allows the filter chain to run.</p>
     */

    @Test
    public void checkFilter_invokesServlet_noUserAgent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        checkFilter_invokesServlet(request, "1.2.3");
    }

    @Test
    public void checkFilter_invokesServlet_otherUserAgent() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X x.y; rv:10.0) Gecko/20100101 Firefox/10.0");
        checkFilter_invokesServlet(request, "1.2.3");
    }

    @Test
    public void checkFilter_invokesServlet_notConfigured() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "HaikuDepot/1.2.3");
        checkFilter_invokesServlet(request, null);
    }

    @Test
    public void checkFilter_invokesServlet_acceptableDesktopApplication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "HaikuDepot/1.2.3");
        checkFilter_invokesServlet(request, "1.2.3");
    }

    @Test
    public void checkFilter_fails_legacyVersion() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "X-HDS-Client");
        checkFilter_fails(request);
    }

    @Test
    public void checkFilter_fails_unacceptableDesktopApplication() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.USER_AGENT, "HaikuDepot/1.2");
        checkFilter_fails(request);
    }

}
