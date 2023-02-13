/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver;

import jakarta.servlet.*;

/**
 * <p>This servlet is here just to capture if it gets hit or not.  It can be used for testing
 * filters.</p>
 */

public class MockServlet implements Servlet {

    private boolean wasInvoked = false;

    @Override
    public void init(ServletConfig config) {
    }

    @Override
    public ServletConfig getServletConfig() {
        return null;
    }

    @Override
    public void service(ServletRequest req, ServletResponse res) {
        wasInvoked = true;
    }

    @Override
    public String getServletInfo() {
        return null;
    }

    @Override
    public void destroy() {
    }

    public boolean wasInvoked() {
        return wasInvoked;
    }
}
