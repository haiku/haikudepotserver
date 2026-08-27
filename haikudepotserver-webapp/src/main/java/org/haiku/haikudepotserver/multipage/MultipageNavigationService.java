/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.haiku.haikudepotserver.multipage.controller.AboutController;
import org.haiku.haikudepotserver.multipage.controller.PkgViewController;
import org.haiku.haikudepotserver.multipage.controller.UserUsageConditionsAgreeController;
import org.haiku.haikudepotserver.multipage.controller.UserViewController;
import org.haiku.haikudepotserver.multipage.model.*;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.haiku.haikudepotserver.user.controller.UserController;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class MultipageNavigationService {

    private final MultipageSecurityService multipageSecurityService;

    public MultipageNavigationService(MultipageSecurityService multipageSecurityService) {
        this.multipageSecurityService = Preconditions.checkNotNull(multipageSecurityService);
    }

    public Map<String, String> createRelayParameters(HttpServletRequest request) {
        return MultipageConstants.KEYS_RELAY_PARAMETERS.stream()
                .map(k -> Pair.of(k, request.getParameter(k)))
                .filter(p -> StringUtils.isNotBlank(p.getValue()))
                .collect(Collectors.toUnmodifiableMap(Pair::getKey, Pair::getValue));
    }

    public List<MenuGroup> deriveMenuGroups(
            @Nullable HttpServletRequest request) {
        return List.of(
                new MenuGroup(
                        null,
                        List.of(
                                new MenuItem(NavigationDestination.HOME, homeUri(request)),
                                new MenuItem(NavigationDestination.ABOUT, aboutUri(request)),
                                new MenuItem(NavigationDestination.ABOUT_HAIKU, aboutHaikuUri())
                        )
                ),
                createUserAndPrivacyMenuGroup(request),
                new MenuGroup(
                        "mp.menu.groups.packages.title",
                        List.of(
                                new MenuItem(NavigationDestination.PKG_LIST, pkgListUri(request, null))
                        )
                )
        );
    }

    public static List<MenuGroup> stripDestination(
            List<MenuGroup> menuGroups,
            Set<NavigationDestination> destinations) {
        return menuGroups.stream()
                .map(mg -> MultipageNavigationService.stripDestination(mg, destinations))
                .filter(mg -> !mg.isEmpty())
                .collect(Collectors.toList());
    }

    private static MenuGroup stripDestination(
            MenuGroup menuGroup,
            Set<NavigationDestination> destinations) {
        return new MenuGroup(
                menuGroup.titleKey(),
                menuGroup.items().stream()
                        .filter(mi -> !destinations.contains(mi.navigationDestination()))
                        .toList());
    }

    private UriComponentsBuilder baselineUri(@Nullable HttpServletRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance();

        if (null != request) {
            createRelayParameters(request).forEach(builder::queryParam);
        }

        return builder;
    }

    public UriComponentsBuilder baselineMultipageUri(@Nullable HttpServletRequest request) {
        return baselineUri(request).pathSegment(MultipageConstants.SEGMENT_MULTIPAGE);
    }

    public UserAndNavigation getUserAndNavigation(
            HttpServletRequest httpServletRequest
    ) {
        User user = multipageSecurityService.tryObtainAuthenticatedUser().orElse(null);
        return new UserAndNavigation(
                user,
                null == user ? null : userViewUri(httpServletRequest, user.nickname()).build(),
                logoutUri(httpServletRequest, null).build(),
                loginUri(httpServletRequest).build()
        );
    }

    public UriComponentsBuilder userViewUri(@Nullable HttpServletRequest request, String nickname) {
        Preconditions.checkArgument(nickname != null);
        return baselineMultipageUri(request).pathSegment(UserViewController.SEGMENT_USER, nickname);
    }

    public UriComponentsBuilder logoutUri(@Nullable HttpServletRequest request, String redirectUri) {
        UriComponentsBuilder builder = baselineUri(request).pathSegment(WebConstants.SEGMENT_SECURITY, WebConstants.SEGMENT_LOGOUT);
        if (StringUtils.isNotBlank(redirectUri)) {
            builder.queryParam(WebConstants.KEY_REDIRECT_URI, redirectUri);
        }
        return builder;
    }

    public UriComponentsBuilder loginUri(@Nullable HttpServletRequest request) {
        return baselineUri(request).pathSegment(WebConstants.SEGMENT_SECURITY, WebConstants.SEGMENT_LOGIN);
    }

    public UriComponentsBuilder homeUri(@Nullable HttpServletRequest request) {
        return baselineMultipageUri(request);
    }

    private UriComponentsBuilder aboutUri(@Nullable HttpServletRequest request) {
        return baselineMultipageUri(request).pathSegment(AboutController.SEGMENT_ABOUT);
    }

    private UriComponentsBuilder aboutHaikuUri() {
        return UriComponentsBuilder.fromUriString("https://www.haiku-os.org");
    }

    public UriComponentsBuilder agreeUserUsageConditionsUri(@Nullable HttpServletRequest request) {
        return baselineMultipageUri(request).pathSegment(UserUsageConditionsAgreeController.SEGMENT_USER_USAGE_CONDITIONS_AGREE);
    }

    /**
     * @param code is the code of the user usage conditions to view; if {@code null} then show the latest.
     */
    public UriComponentsBuilder userUsageConditionsUri(@Nullable String code) {
        return UriComponentsBuilder.newInstance().pathSegment(
                UserController.SEGMENT_USER, "usageconditions",
                Optional.ofNullable(StringUtils.trimToNull(code)).orElse(UserController.LATEST),
                "document.html");
    }

    public UriComponentsBuilder pkgListUri(@Nullable HttpServletRequest request, String searchExpression) {
        UriComponentsBuilder builder = baselineMultipageUri(request).pathSegment("pkg");
        if (StringUtils.isNotBlank(searchExpression)) {
            builder.queryParam(MultipageConstants.KEY_SEARCHEXPRESSION, searchExpression);
        }
        return builder;
    }

    public UriComponentsBuilder pkgViewUri(
            @Nullable HttpServletRequest request,
            String pkgName,
            String repositorySourceCode,
            String architectureCode,
            VersionCoordinates versionCoordinates) {
        Preconditions.checkArgument(StringUtils.isNotBlank(pkgName));

        UriComponentsBuilder builder = baselineMultipageUri(request).pathSegment("pkg", pkgName);

        if (StringUtils.isNotBlank(repositorySourceCode)) {
            builder.queryParam(PkgViewController.KEY_REPOSITORYSOURCECODE, repositorySourceCode);
        }

        if (StringUtils.isNotBlank(architectureCode)) {
            builder.queryParam(PkgViewController.KEY_ARCHITECTURECODE, architectureCode);
        }

        if (null != versionCoordinates) {
            appendVersionParams(builder, versionCoordinates);
        }

        return builder;
    }

    public UriComponentsBuilder pkgChangelogUri(
            @Nullable HttpServletRequest request,
            String pkgName) {
        Preconditions.checkArgument(StringUtils.isNotBlank(pkgName));
        return baselineMultipageUri(request).pathSegment("pkg", pkgName, "changelog");
    }

    private MenuGroup createUserAndPrivacyMenuGroup(@Nullable HttpServletRequest request) {
        List<MenuItem> items = new ArrayList<>();

        items.add(new MenuItem(NavigationDestination.USER_USAGE_CONDITIONS, userUsageConditionsUri(null)));

        multipageSecurityService.tryObtainAuthenticatedUser().ifPresentOrElse(
                user -> {
                    items.add(new MenuItem(
                            NavigationDestination.USER_CURRENT,
                            baselineMultipageUri(request).pathSegment(UserViewController.SEGMENT_USER, user.nickname())));
                    items.add(new MenuItem(NavigationDestination.LOGOUT, logoutUri(request, null)));
                }, () -> {
                    items.add(new MenuItem(NavigationDestination.LOGIN, loginUri(request)));
                });

        return new MenuGroup(
                "mp.menu.group.user_and_privacy.title",
                Collections.unmodifiableList(items)
        );
    }

    private void appendVersionParams(UriComponentsBuilder builder, VersionCoordinates versionCoordinates) {
        if (null != versionCoordinates) {
            builder.queryParam(MultipageConstants.KEY_VERSION_MAJOR, versionCoordinates.getMajor());

            if (null != versionCoordinates.getMinor()) {
                builder.queryParam(MultipageConstants.KEY_VERSION_MINOR, versionCoordinates.getMinor());
            }

            if (null != versionCoordinates.getMicro()) {
                builder.queryParam(MultipageConstants.KEY_VERSION_MICRO, versionCoordinates.getMicro());
            }

            if (null != versionCoordinates.getPreRelease()) {
                builder.queryParam(MultipageConstants.KEY_VERSION_PRERELEASE, versionCoordinates.getPreRelease());
            }

            if (null != versionCoordinates.getRevision()) {
                builder.queryParam(MultipageConstants.KEY_VERSION_REVISION, versionCoordinates.getRevision());
            }
        }
    }

}
