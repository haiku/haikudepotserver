/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.MultipageDomain;
import org.haiku.haikudepotserver.security.model.UserAuthenticationService;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * <p>This is a temporary controller / template that will connect and existing
 * HDS user with the Haiku SSO user ahead of the data migration. It is intended to be
 * self-contained and will be dropped in the next version once all the necessary users
 * have linked their HDS and Haiku-SSO identities.</p>
 */
@Controller
@Deprecated
@RequestMapping(MultipageConstants.SEGMENT_MULTIPAGE + "/" + LinkToSsoController.SEGMENT_LINK_TO_SSO)
public class LinkToSsoController {

    protected final static Logger LOGGER = LoggerFactory.getLogger(LinkToSsoController.class);

    public final static String SEGMENT_LINK_TO_SSO = "link-to-sso";

    private final static String SEGMENT_LOGIN_SSO = "login-sso";
    private final static String SEGMENT_LOGIN = "login";
    private final static String SEGMENT_CONFIRM = "confirm";
    private final static String SEGMENT_COMPLETE = "complete";

    private final ServerRuntime serverRuntime;
    private final UserAuthenticationService userAuthenticationService;
    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final MultipageWebResourceService multipageWebResourceService;
    private final MultipageNavigationService multipageNavigationService;

    public LinkToSsoController(
            ServerRuntime serverRuntime,
            UserAuthenticationService userAuthenticationService,
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            MultipageWebResourceService multipageWebResourceService,
            MultipageNavigationService multipageNavigationService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.userAuthenticationService = Preconditions.checkNotNull(userAuthenticationService);
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
        this.multipageNavigationService = Preconditions.checkNotNull(multipageNavigationService);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView entry(
            HttpServletRequest httpServletRequest,
            Locale locale) {

        InternationalizationSupplier internationalizationSupplier = internationalizationSupplierFactory.create(locale);

        OAuth2User oauth2User = tryGetOAuth2User().orElse(null);

        // if there is no OAuth2 user then clear the context.

        if (null == oauth2User) {
            SecurityContextHolder.clearContext();
        }

        return new ModelAndView(
                "multipage/link-to-sso",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, oauth2User, false),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplier,
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    /**
     * This will spring-board to the Haiku SSO login page. By storing the URL to return to in the session, it's
     * possible to get back to the start.
     */

    @RequestMapping(method = RequestMethod.GET, path = LinkToSsoController.SEGMENT_LOGIN_SSO)
    public ModelAndView redirectLoginSso(HttpServletRequest httpServletRequest) {

        httpServletRequest.getSession().setAttribute(
                WebConstants.KEY_FINAL_REDIRECT_URI,
                UriComponentsBuilder.newInstance()
                        .pathSegment(MultipageConstants.SEGMENT_MULTIPAGE, SEGMENT_LINK_TO_SSO)
                        .build()
                        .toUriString());

        UriComponents loginSsoUriComponents = UriComponentsBuilder.newInstance()
                .pathSegment(WebConstants.SEGMENT_SECURITY, "oauth2", "authorization", "haiku")
                .build();

        LOGGER.info("redirecting to login sso");

        return new ModelAndView(
                "redirect:%s".formatted(loginSsoUriComponents.toUriString()),
                Map.of()
        );
    }

    /**
     * <p>This endpoint gets hit when the user attempts to authenticate.</p>
     */
    @RequestMapping(
            method = RequestMethod.POST,
            path = LinkToSsoController.SEGMENT_LOGIN,
            produces = MediaType.TEXT_HTML_VALUE,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ModelAndView loginUser(
            HttpServletRequest httpServletRequest,
            Locale locale,
            LoginUserForm form
    ) {
        Preconditions.checkArgument(null != form, "a form must be supplied");

        OAuth2User oauth2User = tryGetOAuth2User().orElse(null);

        if (null == oauth2User) {
            throw new IllegalStateException("the sso user is required");
        }

        ObjectContext context = serverRuntime.newContext();
        org.haiku.haikudepotserver.dataobjects.User persistedUser = Optional.ofNullable(form.getNickname())
                .flatMap(n -> org.haiku.haikudepotserver.dataobjects.User.tryGetByNickname(context, n))
                .orElse(null);
        User user = null;
        boolean successfulLogin = false;

        if (null != persistedUser) {
            successfulLogin = userAuthenticationService.matchPassword(persistedUser, form.getPassword());

            if (successfulLogin) {
                user = new User(persistedUser.getNickname(), StringUtils.isNotBlank(persistedUser.getSsoIdentifier()));
                httpServletRequest.getSession().setAttribute(
                        MultipageDomain.LINK_TO_SSO.getAttributeName(),
                        new LinkToSsoSessionData(user)
                );

                LOGGER.info("login successful for [{}]", persistedUser.getNickname());
            } else {
                LOGGER.info("login failed for [{}]", form.getNickname());
            }
        }

        return new ModelAndView(
                "multipage/link-to-sso",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, oauth2User, user, !successfulLogin),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    /**
     * <p>When somebody opts to confirm the linkage between their SSO user and the HDS user,
     * this action will be invoked.</p>
     */
    @RequestMapping(
            method = RequestMethod.POST,
            path = LinkToSsoController.SEGMENT_CONFIRM,
            produces = MediaType.TEXT_HTML_VALUE,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ModelAndView confirm(
            HttpServletRequest httpServletRequest,
            Locale locale
    ) {
        OAuth2User oauth2User = tryGetOAuth2User().orElse(null);

        if (null == oauth2User) {
            throw new IllegalStateException("the sso user is required");
        }

        ObjectContext context = serverRuntime.newContext();
        final User user = tryGetUserFromSession(httpServletRequest)
                .orElseThrow(() -> new IllegalStateException("the user was expected on the session"));
        final org.haiku.haikudepotserver.dataobjects.User persistedUser = Optional.ofNullable(user.nickname())
                .flatMap(n -> org.haiku.haikudepotserver.dataobjects.User.tryGetByNickname(context, n))
                .orElseThrow(() -> new IllegalStateException("the user [%s] was unable to be found".formatted(user.nickname())));

        if (StringUtils.isNotBlank(persistedUser.getSsoIdentifier())) {
            throw new IllegalStateException("the user [%s] is already linked to an sso account".formatted(user.nickname()));
        }

        String ssoIdentifier = oauth2User.getAttribute("sub");

        if (StringUtils.isBlank(ssoIdentifier)) {
            throw new IllegalStateException("the sso user has no identifier");
        }

        persistedUser.setSsoIdentifier(ssoIdentifier);
        context.commitChanges();

        LOGGER.info("did link sso identifier [{}] with HDS user [{}]", persistedUser.getSsoIdentifier(), persistedUser.getNickname());

        User userAfter = new User(user.nickname(), true);

        httpServletRequest.getSession().setAttribute(
                MultipageDomain.LINK_TO_SSO.getAttributeName(),
                new LinkToSsoSessionData(userAfter)
        );

        return new ModelAndView(
                "multipage/link-to-sso",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, oauth2User, userAfter, false),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    @RequestMapping(method = RequestMethod.GET, path = LinkToSsoController.SEGMENT_COMPLETE)
    public ModelAndView redirectComplete(HttpServletRequest httpServletRequest) {

        Optional.ofNullable(httpServletRequest.getSession(false))
                .ifPresent(HttpSession::invalidate);

        LOGGER.info("did terminate session and redirect to the root");

        return new ModelAndView(
                "redirect:/",
                Map.of()
        );
    }

    private LinkToSsoData createData(
            HttpServletRequest httpServletRequest,
            @Nullable OAuth2User oauth2User,
            boolean failedLogin
    ) {
        return createData(
                httpServletRequest,
                oauth2User,
                tryGetUserFromSession(httpServletRequest).orElse(null),
                failedLogin
        );
    }

    private LinkToSsoData createData(
            HttpServletRequest httpServletRequest,
            @Nullable OAuth2User oauth2User,
            @Nullable User user,
            boolean failedLogin
    ) {
        SsoUser ssoUser = Optional.ofNullable(oauth2User).map(this::mapToSsoUser).orElse(null);
        return new LinkToSsoData(
                multipageNavigationService.userUsageConditionsUri(null).build(),
                createStep(ssoUser, user),
                user,
                createLoginActionUri(),
                failedLogin,
                ssoUser,
                createSsoLoginUri(),
                createConfirmActionUri(),
                createCompleteUri()
        );
    }

    private Optional<User> tryGetUserFromSession(HttpServletRequest httpServletRequest) {
        return Optional.ofNullable(httpServletRequest.getSession(false))
                .map(s -> s.getAttribute(MultipageDomain.LINK_TO_SSO.getAttributeName()))
                .map(o -> (LinkToSsoSessionData) o)
                .map(LinkToSsoSessionData::user);
    }

    private Optional<OAuth2User> tryGetOAuth2User() {
        return Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .filter(Authentication::isAuthenticated)
                .filter(a -> a instanceof OAuth2AuthenticationToken)
                .map(OAuth2AuthenticationToken.class::cast)
                .map(OAuth2AuthenticationToken::getPrincipal);
    }

    private SsoUser mapToSsoUser(OAuth2User user) {
        String username = user.getAttribute("preferred_username");

        if (StringUtils.isBlank(username)) {
            throw new IllegalStateException("the username is missing for the sso user.");
        }

        return new SsoUser(username);
    }

    private UriComponents createSsoLoginUri() {
        return UriComponentsBuilder.newInstance()
                .pathSegment(MultipageConstants.SEGMENT_MULTIPAGE, SEGMENT_LINK_TO_SSO, SEGMENT_LOGIN_SSO)
                .build();
    }

    private UriComponents createLoginActionUri() {
        return UriComponentsBuilder.newInstance()
                .pathSegment(MultipageConstants.SEGMENT_MULTIPAGE, SEGMENT_LINK_TO_SSO, SEGMENT_LOGIN)
                .build();
    }

    private UriComponents createConfirmActionUri() {
        return UriComponentsBuilder.newInstance()
                .pathSegment(MultipageConstants.SEGMENT_MULTIPAGE, SEGMENT_LINK_TO_SSO, SEGMENT_CONFIRM)
                .build();
    }

    /**
     * <p>This is the URI to go back somewhere else once the process is complete.</p>
     */
    private UriComponents createCompleteUri() {
        return UriComponentsBuilder.newInstance()
                .pathSegment(MultipageConstants.SEGMENT_MULTIPAGE, SEGMENT_LINK_TO_SSO, SEGMENT_COMPLETE)
                .build();
    }

    private Step createStep(
            @Nullable SsoUser ssoUser,
            @Nullable User user
    ) {
        if (null == ssoUser) {
                return Step.LOGIN_SSO;
            }

            if (null == user) {
                return Step.LOGIN_USER;
            }

            return user.hasSsoIdentifier() ? Step.COMPLETE : Step.CONFIRM;
        }

        public enum Step {
            LOGIN_SSO,
            LOGIN_USER,
            CONFIRM,
        COMPLETE
    }

    public record User(
            String nickname,
            boolean hasSsoIdentifier
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

    }

    public record SsoUser(
            String username
    ) {
    }

    public record LinkToSsoData(
            UriComponents userUsageConditionsUriComponents,
            Step step,
            @Nullable User user,
            UriComponents loginActionUriComponents,
            boolean failedLogin,
            @Nullable SsoUser ssoUser,
            UriComponents ssoLoginUriComponents,
            UriComponents confirmLinkActionUriComponents,
            UriComponents completeUriComponents
    ) {
    }

    /**
     * <p>This is a data model that gets stored in the session.</p>
     */
    public record LinkToSsoSessionData(
            @Nullable User user
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

    }

    /**
     * This is used as a data-model for the login user form on the page.
     */
    public static class LoginUserForm {

        private String nickname;
        private String password;

        public String getNickname() {
            return nickname;
        }

        public void setNickname(String nickname) {
            this.nickname = nickname;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

}
