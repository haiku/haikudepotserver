/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.controller.UserUsageConditionsAgreeController;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.haiku.haikudepotserver.user.model.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

public class AuthenticationSuccessHandler implements org.springframework.security.web.authentication.AuthenticationSuccessHandler {

    protected final static Logger LOGGER = LoggerFactory.getLogger(AuthenticationSuccessHandler.class);

    private final UserService userService;
    private final ServerRuntime serverRuntime;

    public AuthenticationSuccessHandler(
            ServerRuntime serverRuntime,
            UserService userService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.userService = Preconditions.checkNotNull(userService);
    }

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        if (hasAgreedToLatestUserUsageConditionsOrSkip(authentication)) {
            response.sendRedirect(deriveRedirectUri(request));
        } else {
            LOGGER.info("user has not agreed to user usage conditions -> redirect to agreement page");
            UriComponents redirectUri = UriComponentsBuilder.newInstance()
                    .pathSegment(
                            MultipageConstants.SEGMENT_MULTIPAGE,
                            UserUsageConditionsAgreeController.SEGMENT_USER_USAGE_CONDITIONS_AGREE
                    )
                    .queryParam(
                            WebConstants.KEY_REDIRECT_URI,
                            URLEncoder.encode(deriveRedirectUri(request), StandardCharsets.UTF_8))
                    .build();
            response.sendRedirect(redirectUri.toUriString());
        }
    }

    private String deriveRedirectUri(HttpServletRequest request) {
        String uri = request.getParameter(WebConstants.KEY_REDIRECT_URI);

        if (StringUtils.isBlank(uri)) {
            uri = Optional.ofNullable(request.getSession(false))
                    .map(s -> s.getAttribute(WebConstants.KEY_FINAL_REDIRECT_URI))
                    .map(Object::toString)
                    .filter(StringUtils::isNotBlank)
                    .orElse(null);
        }

        if (StringUtils.isBlank(uri)) {
            uri = "/%s".formatted(MultipageConstants.SEGMENT_MULTIPAGE);
        }

        return uri;
    }

    /**
     * <p>Check to make sure that the {@code authentication}'s user has agreed to the latest user usage
     * conditions.</p>
     */
    private boolean hasAgreedToLatestUserUsageConditionsOrSkip(Authentication authentication) {
        ObjectContext context = serverRuntime.newContext();
        User user = AuthenticationHelper.tryGetUserForAuthentication(context, authentication).orElse(null);

        if (null != user) {
            if (!user.getActive()) {
                throw new IllegalStateException("user [%s] authenticated but is not active".formatted(user));
            }

            if (user.getIsRoot()) {
                return true;
            }

            return userService.isUserCurrentlyAgreeingToCurrentUserUsageConditions(user);
        }

        return true;
    }

}
