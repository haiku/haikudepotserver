/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage;

import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.support.web.NaturalLanguageWebHelper;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Locale;

public class MultipageLocaleResolver implements org.springframework.web.servlet.LocaleResolver {

    @Resource
    ServerRuntime serverRuntime;

    @Override
    public Locale resolveLocale(HttpServletRequest request) {
        return NaturalLanguageWebHelper.deriveNaturalLanguage(serverRuntime.getContext(), request).toLocale();
    }

    @Override
    public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
        // ignore.
    }
}
