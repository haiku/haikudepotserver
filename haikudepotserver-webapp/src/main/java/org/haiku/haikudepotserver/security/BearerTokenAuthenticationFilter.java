/*
 * Copyright 2020-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

/**
 * <p>This filter will find any <code>Authorization</code> headers that have a JWT
 * bearer-token and will turn that into an {@link org.springframework.security.core.Authentication}
 * on the {@link SecurityContextHolder}.</p>
 */

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX_BEARER = "Bearer ";

    private final UserAuthenticationService userAuthenticationService;

    public BearerTokenAuthenticationFilter(UserAuthenticationService userAuthenticationService) {
        this.userAuthenticationService = Preconditions.checkNotNull(userAuthenticationService);
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

    private Optional<Authentication> maybeCreateAuthentication(HttpServletRequest httpServletRequest) {
        return Optional.ofNullable(httpServletRequest.getHeader(HttpHeaders.AUTHORIZATION))
                .map(StringUtils::trimToNull)
                .filter(h -> h.startsWith(PREFIX_BEARER))
                .map(h -> h.substring(PREFIX_BEARER.length()))
                .flatMap(this::maybeCreateAuthentication);
    }

    private Optional<Authentication> maybeCreateAuthentication(String token) {
        return userAuthenticationService.authenticateByToken(token)
                .map(UserAuthentication::new);
    }

}
