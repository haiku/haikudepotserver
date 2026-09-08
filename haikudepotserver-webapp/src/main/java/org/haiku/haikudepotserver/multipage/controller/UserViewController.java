/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.haiku.haikudepotserver.api2.UserApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.*;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.MenuGroup;
import org.haiku.haikudepotserver.multipage.model.NavigationDestination;
import org.haiku.haikudepotserver.multipage.model.UserAndNavigation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + UserViewController.SEGMENT_USER)
public class UserViewController {

    public final static String SEGMENT_USER = "user";

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final UserApiService userApiService;
    private final MultipageNavigationService navigationService;
    private final MultipageWebResourceService multipageWebResourceService;
    private final MultipageSecurityService multipageSecurityService;

    public UserViewController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            UserApiService userApiService,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService,
            MultipageSecurityService multipageSecurityService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.userApiService = Preconditions.checkNotNull(userApiService);
        this.navigationService = Preconditions.checkNotNull(navigationService);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
        this.multipageSecurityService = Preconditions.checkNotNull(multipageSecurityService);
    }

    @RequestMapping(value = "{nickname}", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView viewUser(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String nickname) throws MultipageObjectNotFoundException {
        return new ModelAndView(
                "multipage/user-view",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(
                                httpServletRequest,
                                locale,
                                nickname
                        ),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private UserViewData createData(
            HttpServletRequest httpServletRequest,
            Locale locale,
            String nickname) {

        GetUserRequestEnvelope userRequest = new GetUserRequestEnvelope();
        userRequest.setNickname(nickname);
        GetUserResult userResult = userApiService.getUser(userRequest);
        boolean isAuthenticatedUser = multipageSecurityService.tryObtainAuthenticatedUser()
                .map(u -> userResult.getNickname().equals(u.nickname()))
                .orElse(false);

        return new UserViewData(
                ServletUriComponentsBuilder.fromRequest(httpServletRequest).build(),
                MultipageNavigationService.stripDestination(
                        navigationService.deriveMenuGroups(httpServletRequest),
                        EnumSet.of(NavigationDestination.USER_CURRENT)
                ),
                navigationService.getUserAndNavigation(httpServletRequest),
                userResult.getNickname(),
                userResult.getActive(),
                userResult.getIsRoot(),
                Instant.ofEpochMilli(userResult.getCreateTimestamp()),
                Instant.ofEpochMilli(userResult.getModifyTimestamp()),
                Optional.ofNullable(userResult.getLastAuthenticationTimestamp())
                        .map(Instant::ofEpochMilli)
                        .orElse(null),
                isAuthenticatedUser,
                navigationService.logoutUri(httpServletRequest, null).build(),
                UriComponentsBuilder
                        .fromUriString("/#!/user/%s/changepassword?bcguid=%s".formatted(
                                userResult.getNickname(),
                                RandomStringUtils.insecure().nextAlphabetic(5)))
                        .build(),
                tryCreateUserUsageConditionsAgreement(userResult.getUserUsageConditionsAgreement()).orElse(null),
                navigationService.agreeUserUsageConditionsUri(httpServletRequest).build()
        );
    }

    public Optional<UserUsageConditionsAgreement> tryCreateUserUsageConditionsAgreement(
            @Nullable GetUserResultUserUsageConditionsAgreement userResultUserUsageConditionsAgreement) {

        if (null == userResultUserUsageConditionsAgreement) {
            return Optional.empty();
        }

        String code = userResultUserUsageConditionsAgreement.getUserUsageConditionsCode();

        return Optional.of(new UserUsageConditionsAgreement(
                Instant.ofEpochMilli(userResultUserUsageConditionsAgreement.getTimestampAgreed()),
                code,
                getMinimumAge(code),
                userResultUserUsageConditionsAgreement.getIsLatest(),
                navigationService.userUsageConditionsUri(code).build()
        ));
    }

    public Integer getMinimumAge(String userUsageConditionsCode) {
            return userApiService.getUserUsageConditions(
                    new GetUserUsageConditionsRequestEnvelope()
                            .code(userUsageConditionsCode)
            ).getMinimumAge();
    }

    /**
     * <p>This data structure contains information about the usage conditions that the user has already agreed to.</p>
     */
    public record UserUsageConditionsAgreement(
        Instant timestampAgreed,
        String userUsageConditionsCode,
        int minimumAge,
        boolean isLatest,
        UriComponents userUsageConditionsUriComponents
    ) {
    }

    /**
     * <p>This is the data model for the page to be rendered from.</p>
     */

    public record UserViewData(
            UriComponents uriComponents,
            List<MenuGroup> menuGroups,
            UserAndNavigation userAndNavigation,

            String nickname,
            boolean active,
            boolean isRoot,
            Instant createTimestamp,
            Instant modifyTimestamp,
            Instant lastAuthenticationTimestamp,

            boolean isAuthenticatedUser,

            UriComponents logoutUriComponents,
            UriComponents changePasswordUriComponents,

            @Nullable UserUsageConditionsAgreement userUsageConditionsAgreement,

            UriComponents agreeLatestUserUsageConditionsUriComponents
    ) {
    }

}
