/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.UserApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.multipage.MultipageSecurityService;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.validation.model.StandardValidationFailure;
import org.haiku.haikudepotserver.multipage.validation.model.ValidationFailure;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + UserUsageConditionsAgreeController.SEGMENT_USER_USAGE_CONDITIONS_AGREE)
public class UserUsageConditionsAgreeController {

    protected final static Logger LOGGER = LoggerFactory.getLogger(UserUsageConditionsAgreeController.class);

    public final static String SEGMENT_USER_USAGE_CONDITIONS_AGREE = "user_usage_conditions_agree";

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final UserApiService userApiService;
    private final MultipageNavigationService navigationService;
    private final MultipageWebResourceService multipageWebResourceService;

    public UserUsageConditionsAgreeController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            UserApiService userApiService,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService,
            MultipageSecurityService multipageSecurityService) {
        this.internationalizationSupplierFactory = internationalizationSupplierFactory;
        this.userApiService = userApiService;
        this.navigationService = navigationService;
        this.multipageWebResourceService = multipageWebResourceService;
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView goAgreeEntry(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @RequestParam(value = WebConstants.KEY_REDIRECT_URI, required = false) String redirectUri) {
        return goAgreePage(
                httpServletRequest,
                locale,
                redirectUri,
                new FormData(false, List.of(), false, List.of())
        );
    }

    @RequestMapping(
            method = RequestMethod.POST,
            produces = MediaType.TEXT_HTML_VALUE,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public ModelAndView goAgreeAction(
            HttpServletRequest httpServletRequest,
            Locale locale,
            AgreeForm form) {
        Preconditions.checkArgument(null != form.getAction(), "a form action must be supplied");

        return switch (form.getAction()) {
            case LOGOUT -> {
                LOGGER.info("the user has opted to logout");
                yield goLogout(httpServletRequest);
            }

            case AGREE -> {
                List<ValidationFailure> isAgreedFailures = List.of();
                List<ValidationFailure> isAgreedMinimumAgeFailures = List.of();

                if (!form.isAgreed()) {
                    isAgreedFailures = List.of(new StandardValidationFailure(StandardValidationFailure.Type.REQUIRED_TRUE));
                }

                if (!form.isAgreedMinimumAge()) {
                    isAgreedMinimumAgeFailures = List.of(new StandardValidationFailure(StandardValidationFailure.Type.REQUIRED_TRUE));
                }

                if (!isAgreedFailures.isEmpty() || !isAgreedMinimumAgeFailures.isEmpty()) {
                    LOGGER.info("the user has not selected both checkboxes -> back to the form");
                    yield goAgreePage(
                            httpServletRequest,
                            locale,
                            form.getRedirectUri(),
                            new FormData(
                                    form.isAgreed(),
                                    isAgreedFailures,
                                    form.isAgreedMinimumAge(),
                                    isAgreedMinimumAgeFailures
                            )
                    );
                }

                yield goAgreeConfirmedAction(
                        httpServletRequest,
                        form.getCode(),
                        form.getRedirectUri()
                );
            }
        };
    }

    /**
     * <p>This will actually agree the user to the conditions and take them to the page that they
     * had requested.</p>
     */
    private ModelAndView goAgreeConfirmedAction(
            HttpServletRequest httpServletRequest,
            String userUsageConditionsCode,
            String redirectUri
    ) {
        String nickname = getCurrentUserNickname();
        userApiService.agreeUserUsageConditions(new AgreeUserUsageConditionsRequestEnvelope()
                .nickname(nickname)
                .userUsageConditionsCode(userUsageConditionsCode)
        );

        LOGGER.info("user [{}] agreed to user usage conditions [{}]", nickname, userUsageConditionsCode);

        return deriveRedirectUrlModelAndView(httpServletRequest, redirectUri);
    }

    private ModelAndView goLogout(HttpServletRequest httpServletRequest) {
        UriComponents logoutUri = navigationService.logoutUri(httpServletRequest,null).build();
        return new ModelAndView(
                "redirect:%s".formatted(logoutUri.getPath()),
                logoutUri.getQueryParams()
        );
    }

    private ModelAndView goAgreePage(
            HttpServletRequest httpServletRequest,
            Locale locale,
            String redirectUri,
            FormData formData
            ) {

        LatestUserUsageConditionsData latestUserUsageConditions = createLatestUserUsageConditionsData(httpServletRequest);
        AuthenticatedUserData authenticatedUser = createAuthenticatedUserData(latestUserUsageConditions);

        if (authenticatedUser.authenticatedUserIsRoot() || authenticatedUser.authenticatedUserHasAgreedLatest()) {
            LOGGER.debug("user [{}] is root or has already agreed to the usage conditions", authenticatedUser.nickname());
            return deriveRedirectUrlModelAndView(httpServletRequest, redirectUri);
        }

        // we need to check to see if the current user

        return new ModelAndView(
                "multipage/user-usage-conditions-agree",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(
                                httpServletRequest,
                                redirectUri,
                                authenticatedUser,
                                latestUserUsageConditions,
                                formData
                        ),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private AgreeData createData(
            HttpServletRequest httpServletRequest,
            String redirectUri,
            AuthenticatedUserData authenticatedUserData,
            LatestUserUsageConditionsData latestUserUsageConditionsData,
            FormData formData) {
        return new AgreeData(
                ServletUriComponentsBuilder.fromRequest(httpServletRequest).build(),
                authenticatedUserData,
                redirectUri,
                navigationService
                        .baselineMultipageUri(httpServletRequest)
                        .pathSegment(SEGMENT_USER_USAGE_CONDITIONS_AGREE)
                        .build(),
                latestUserUsageConditionsData,
                formData
        );
    }

    private LatestUserUsageConditionsData createLatestUserUsageConditionsData(HttpServletRequest httpServletRequest) {
        GetUserUsageConditionsResult userUsageConditionsResult
                = userApiService.getUserUsageConditions(new GetUserUsageConditionsRequestEnvelope());
        return new LatestUserUsageConditionsData(
                userUsageConditionsResult.getCode(),
                userUsageConditionsResult.getMinimumAge(),
                navigationService.userUsageConditionsUri(null).build()
        );
    }

    private AuthenticatedUserData createAuthenticatedUserData(
            LatestUserUsageConditionsData latestUserUsageConditions) {
        GetUserResult userResult = userApiService.getUser(new GetUserRequestEnvelope()
                .nickname(getCurrentUserNickname()));
        return new AuthenticatedUserData(
                userResult.getNickname(),
                userResult.getIsRoot(),
                Optional.ofNullable(userResult.getUserUsageConditionsAgreement())
                        .map(GetUserResultUserUsageConditionsAgreement::getUserUsageConditionsCode)
                        .map(code -> code.equals(latestUserUsageConditions.code()))
                        .orElse(false)
        );
    }

    private UriComponents deriveRedirectUrl(HttpServletRequest httpServletRequest, String redirectUri) {
        return Optional.ofNullable(redirectUri)
                .filter(StringUtils::isNotBlank)
                .map(u -> UriComponentsBuilder.fromUriString(u).build())
                .orElseGet(() -> navigationService.homeUri(httpServletRequest).build());
    }

    private ModelAndView deriveRedirectUrlModelAndView(HttpServletRequest httpServletRequest, String redirectUri) {
        UriComponents redirectUriComponents = deriveRedirectUrl(httpServletRequest, redirectUri);

        if (null != redirectUriComponents.getScheme()) {
            throw new IllegalStateException("the redirect uri scheme must not be set");
        }

        return new ModelAndView(
                "redirect:%s".formatted(redirectUriComponents.getPath()),
                redirectUriComponents.getQueryParams()
        );
    }

    private String getCurrentUserNickname() {
        return userApiService.getCurrentUser().getNickname();
    }

    public record LatestUserUsageConditionsData (
            String code,
            @Nullable Integer minimumAge,
            UriComponents uriComponents
    ) {
    }

    public record AuthenticatedUserData(
            String nickname,
            boolean authenticatedUserIsRoot,
            boolean authenticatedUserHasAgreedLatest
    ) {
    }

    public record FormData(
            boolean isAgreed,
            List<ValidationFailure> isAgreedFailures,
            boolean isAgreedMinimumAge,
            List<ValidationFailure> isAgreedMinimumAgeFailures
    ) {
    }

    public record AgreeData(
            UriComponents uriComponents,
            AuthenticatedUserData authenticatedUser,
            String redirectUri,
            UriComponents agreeActionUriComponents,
            LatestUserUsageConditionsData latestUserUsageConditions,
            FormData form
    ) {
    }

    public enum Action {
        LOGOUT,
        AGREE
    }

    /**
     * <p>This model class represents the data returned from a form.</p>
     */

    public static class AgreeForm {

        /**
         * <p>This is the code of the agreement that the user was looking at.</p>
         */
        private String code;
        private boolean isAgreed;
        private boolean isAgreedMinimumAge;
        @Nullable private String redirectUri;
        private Action action;

        public Action getAction() {
            return action;
        }

        public void setAction(Action action) {
            this.action = action;
        }

        @Nullable
        public String getRedirectUri() {
            return redirectUri;
        }

        public void setRedirectUri(@Nullable String redirectUri) {
            this.redirectUri = redirectUri;
        }

        public boolean isAgreed() {
            return isAgreed;
        }

        public void setIsAgreed(boolean agreed) {
            isAgreed = agreed;
        }

        public boolean isAgreedMinimumAge() {
            return isAgreedMinimumAge;
        }

        public void setIsAgreedMinimumAge(boolean agreedMinimumAge) {
            isAgreedMinimumAge = agreedMinimumAge;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }
    }

}
