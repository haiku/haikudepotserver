/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.web;

import javax.servlet.http.HttpSessionEvent;
import javax.servlet.http.HttpSessionListener;

/**
 * <p>This application is stateless and as such should not be creating sessions.  This listener will be invoked if a
 * session is created and it can cause a failure such that it is possible to see where the session is being created.
 * </p>
 */

public class SessionListener implements HttpSessionListener {

    @Override
    public void sessionCreated(HttpSessionEvent se) {
        throw new IllegalStateException("this application does not use sessions; one was created.");
    }

    @Override
    public void sessionDestroyed(HttpSessionEvent se) {
    }
}
