/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.model;

import com.google.common.base.CaseFormat;
import org.apache.commons.lang3.Strings;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.controller.LinkToSsoController;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import java.util.Optional;

/**
 * <p>The multipage system is broken down by URL paths. A pattern of paths is known as a
 * {@link MultipageDomain}. Data can be stored under the domain each of which is defined
 * by one of these {@link MultipageDomain} values.</p>
 *
 * <p>If a request comes in for a different domain then the data for the non-matching
 * domains will be removed from the HTTP session.</p>
 */
public enum MultipageDomain {

    LINK_TO_SSO(
            PathPatternRequestMatcher.pathPattern("/%s/%s/**".formatted(
                    MultipageConstants.SEGMENT_MULTIPAGE,
                    LinkToSsoController.SEGMENT_LINK_TO_SSO
            ))
    );

    private final RequestMatcher requestMatcher;

    private final String attributeName;

    MultipageDomain(RequestMatcher requestMatcher) {
        this.requestMatcher = requestMatcher;
        this.attributeName = MultipageConstants.MULTIPAGE_DOMAIN_SESSION_ATTRIBUTE_NAME_PREFIX
                + CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_UNDERSCORE, this.name());
    }

    /**
     * <p>This matcher can be used to check that an {@link jakarta.servlet.http.HttpServletRequest}
     * belongs to this domain.</p>
     */
    public RequestMatcher getRequestMatcher() {
        return requestMatcher;
    }

    /**
     * <p>This is the name of the HTTP Session attribute that can be used with
     * {@link jakarta.servlet.http.HttpSession}.</p>
     */
    public String getAttributeName() {
        return attributeName;
    }

    public static Optional<MultipageDomain> tryValueOfAttributeName(String attributeName) {
        if (Strings.CS.startsWith(attributeName, MultipageConstants.MULTIPAGE_DOMAIN_SESSION_ATTRIBUTE_NAME_PREFIX)) {
            String name = CaseFormat.LOWER_UNDERSCORE.to(
                    CaseFormat.UPPER_UNDERSCORE,
                    attributeName.substring(MultipageConstants.MULTIPAGE_DOMAIN_SESSION_ATTRIBUTE_NAME_PREFIX.length())
            );
            return Optional.of(valueOf(name));
        }

        return Optional.empty();
    }

}
