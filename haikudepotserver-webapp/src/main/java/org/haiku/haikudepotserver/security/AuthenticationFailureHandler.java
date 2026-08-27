/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.DefaultRedirectStrategy;
import org.springframework.security.web.RedirectStrategy;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * <p>This logic is executed when the user fails to authenticate.</p>
 */

public class AuthenticationFailureHandler implements org.springframework.security.web.authentication.AuthenticationFailureHandler {

    private final UriComponents baseFailureUri;
    private final RedirectStrategy redirectStrategy = new DefaultRedirectStrategy();

    public AuthenticationFailureHandler(UriComponents baseFailureUri) {
        Preconditions.checkArgument(null != baseFailureUri);
        Preconditions.checkArgument(null == baseFailureUri.getScheme());
        this.baseFailureUri = baseFailureUri;
    }

    @Override
    public void onAuthenticationFailure(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException exception) throws IOException, ServletException {

        String redirectUri = request.getParameter(WebConstants.KEY_REDIRECT_URI);
        UriComponents failureUri = baseFailureUri;

        if (StringUtils.isNotBlank(redirectUri)) {
            failureUri = UriComponentsBuilder.newInstance().uriComponents(baseFailureUri)
                    .queryParam(WebConstants.KEY_REDIRECT_URI, URLEncoder.encode(redirectUri, StandardCharsets.UTF_8))
                    .build();
        }

        this.redirectStrategy.sendRedirect(request, response, failureUri.toUriString());
    }
}
