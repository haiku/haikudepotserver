/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.multipage;

import org.apache.cayenne.configuration.server.ServerRuntime;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

public class MultipageLocaleResolver implements org.springframework.web.servlet.LocaleResolver {

    @Resource
    ServerRuntime serverRuntime;

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return MultipageHelper.deriveNaturalLanguage(serverRuntime.getContext(), request).toLocale();
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // ignore.
    }
}
