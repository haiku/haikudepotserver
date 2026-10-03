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
 * <p>Matches the path on a request because it has a specified prefix.</p>
 */

public class PrefixPathRequestMatcher implements RequestMatcher {

    private final static Pattern PATTERN = Pattern.compile("^(/[a-z0-9._-]+)+/?$");

    private final String prefix;

    public static PrefixPathRequestMatcher withFirstSegment(String segment) {
        return new PrefixPathRequestMatcher("/%s/".formatted(segment));
    }

    public PrefixPathRequestMatcher(String prefix) {
        Preconditions.checkArgument(prefix != null);
        Preconditions.checkArgument(PATTERN.matcher(prefix).matches(), "bad prefix [%s]", prefix);
        this.prefix = prefix;
    }

    @Override
    public boolean matches(HttpServletRequest request) {
        return request.getRequestURI().startsWith(prefix);
    }
}
