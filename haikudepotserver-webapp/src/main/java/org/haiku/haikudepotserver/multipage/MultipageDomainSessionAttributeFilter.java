/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage;

import com.google.common.collect.Streams;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.haiku.haikudepotserver.multipage.model.MultipageDomain;

import java.io.IOException;
import java.util.Optional;

/**
 * <p>This filter takes care of stripping data from the {@link HttpSession} that doesn't
 * belong to the {@link MultipageDomain} of the current HTTP request's domain.</p>
 * @see MultipageDomain
 */
public class MultipageDomainSessionAttributeFilter extends HttpFilter {

    @Override
    public void doFilter(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws IOException, ServletException {
        clearAttributesOutsideDomain(request);
        chain.doFilter(request,response);
    }

    private void clearAttributesOutsideDomain(HttpServletRequest request) {
        HttpSession session = request.getSession(false);

        if (null != session) {
            Streams.stream(session.getAttributeNames().asIterator())
                    .filter(n -> n.startsWith(MultipageConstants.MULTIPAGE_DOMAIN_SESSION_ATTRIBUTE_NAME_PREFIX))
                    .map(MultipageDomain::tryValueOfAttributeName)
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .filter(e -> !e.getRequestMatcher().matches(request))
                    .forEach(e -> session.removeAttribute(e.getAttributeName()));
        }

    }

}
