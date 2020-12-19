/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.http.HttpMethod;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * <p>This is not ideal, but some GET requests are authenticated using a query
 * parameter.  This should be phased out eventually.</p>
 */

public class QueryParamAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX_SECURED = "/" + WebConstants.PATH_COMPONENT_SECURED + "/";

    private static final String PARAM_BEARER_TOKEN = "hdsbtok";

    private final UserAuthenticationService userAuthenticationService;

    public QueryParamAuthenticationFilter(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = userAuthenticationService;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse,
            FilterChain filterChain) throws ServletException, IOException {
        Authentication authenticationPrior = SecurityContextHolder.getContext().getAuthentication();
        Authentication authentication = null;

        if (null == authenticationPrior || !authenticationPrior.isAuthenticated()) {
            authentication = maybeCreateAuthentication(httpServletRequest).orElse(null);
        }

        try {
            if (null != authentication) {
                authentication.setAuthenticated(true);
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
            filterChain.doFilter(httpServletRequest, httpServletResponse);
        } finally {
            if (null != authentication) {
                SecurityContextHolder.getContext().setAuthentication(authenticationPrior);
            }
        }
    }

    private boolean isApplicable(HttpServletRequest request) {
        if (!HttpMethod.GET.matches(request.getMethod())) {
            return false;
        }
        String filterPathInfo = request.getRequestURI().substring(request.getContextPath().length());
        return filterPathInfo.startsWith(PREFIX_SECURED);
    }

    private Optional<Authentication> maybeCreateAuthentication(HttpServletRequest request) {
        if (!isApplicable(request)) {
            return Optional.empty();
        }
        return Optional.ofNullable(request.getParameter(PARAM_BEARER_TOKEN))
                .map(StringUtils::trimToNull)
                .flatMap(userAuthenticationService::authenticateByToken)
                .map(UserAuthentication::new);
    }

}
