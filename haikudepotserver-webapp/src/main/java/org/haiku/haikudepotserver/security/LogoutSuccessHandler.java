/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.jspecify.annotations.Nullable;
import org.springframework.security.core.Authentication;

import java.io.IOException;

public class LogoutSuccessHandler implements org.springframework.security.web.authentication.logout.LogoutSuccessHandler {

    @Override
    public void onLogoutSuccess(HttpServletRequest request, HttpServletResponse response, @Nullable Authentication authentication) throws IOException, ServletException {
        String redirectUri = request.getParameter(WebConstants.KEY_REDIRECT_URI);

        if (StringUtils.isNotBlank(redirectUri)) {
            response.sendRedirect(redirectUri);
        } else {
            response.sendRedirect("/%s".formatted(MultipageConstants.SEGMENT_MULTIPAGE));
        }

    }
}
