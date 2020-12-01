package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.security.model.AuthenticationService;
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
 * <p>This filter will find any <code>Authorization</code> headers that have a JWT
 * bearer-token and will turn that into an {@link org.springframework.security.core.Authentication}
 * on the {@link SecurityContextHolder}.</p>
 */

public class BearerTokenAuthenticationFilter extends OncePerRequestFilter {

    private static final String PREFIX_BEARER = "Bearer ";

    private final AuthenticationService authenticationService;

    public BearerTokenAuthenticationFilter(AuthenticationService authenticationService) {
        this.authenticationService = Preconditions.checkNotNull(authenticationService);
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
        return authenticationService.authenticateByToken(token)
                .map(UserAuthentication::new);
    }

}
