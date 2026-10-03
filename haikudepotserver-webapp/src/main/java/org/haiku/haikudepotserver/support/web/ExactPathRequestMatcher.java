/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.web;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.regex.Pattern;

/**
 * <p>Matches the exact path of the request.</p>
 */

public class ExactPathRequestMatcher implements RequestMatcher {

    private final static Pattern PATTERN = Pattern.compile("(/[a-z0-9._-]+)+$");

    private final String path;

    public ExactPathRequestMatcher(String prefix) {
        Preconditions.checkArgument(prefix != null);
        Preconditions.checkArgument(PATTERN.matcher(prefix).matches(), "bad path [%s]", prefix);
        this.path = prefix;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return request.getRequestURI().equals(path);
    }

}
