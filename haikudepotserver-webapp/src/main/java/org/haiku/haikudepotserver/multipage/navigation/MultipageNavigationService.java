/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.navigation;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.controller.AboutController;
import org.haiku.haikudepotserver.multipage.controller.PkgViewController;
import org.haiku.haikudepotserver.multipage.model.MenuGroup;
import org.haiku.haikudepotserver.multipage.model.MenuItem;
import org.haiku.haikudepotserver.multipage.model.NavigationDestination;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.user.controller.UserController;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class MultipageNavigationService {

    public Map<String, String> createRelayParameters(HttpServletRequest request) {
        return MultipageConstants.KEYS_RELAY_PARAMETERS.stream()
                .map(k -> Pair.of(k, request.getParameter(k)))
                .filter(p -> StringUtils.isNotBlank(p.getValue()))
                .collect(Collectors.toUnmodifiableMap(Pair::getKey, Pair::getValue));
    }

    public List<MenuGroup> deriveMenuGroups(@Nullable HttpServletRequest request) {
        return List.of(
                new MenuGroup(
                        null,
                        List.of(
                                new MenuItem(NavigationDestination.HOME, homeUri(request)),
                                new MenuItem(NavigationDestination.ABOUT, aboutUri(request)),
                                new MenuItem(NavigationDestination.ABOUT_HAIKU, aboutHaikuUri())
                        )
                ),
                new MenuGroup(
                        "mp.menu.group.user_and_privacy.title",
                        List.of(
                                new MenuItem(NavigationDestination.USER_USAGE_CONDITIONS, userUsageConditionsUri(request))
                        )
                ),
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
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance().pathSegment(MultipageConstants.SEGMENT_MULTIPAGE);

        if (null != request) {
            createRelayParameters(request).forEach(builder::queryParam);
        }

        return builder;
    }

    public UriComponentsBuilder homeUri(@Nullable HttpServletRequest request) {
        return baselineUri(request);
    }

    private UriComponentsBuilder aboutUri(@Nullable HttpServletRequest request) {
        return baselineUri(request).pathSegment(AboutController.SEGMENT_ABOUT);
    }

    private UriComponentsBuilder aboutHaikuUri() {
        return UriComponentsBuilder.fromUriString("https://www.haiku-os.org");
    }

    private UriComponentsBuilder userUsageConditionsUri(@Nullable HttpServletRequest request) {
        return UriComponentsBuilder.newInstance().pathSegment(
                UserController.SEGMENT_USER, "usageconditions", UserController.LATEST, "document.html");
    }

    public UriComponentsBuilder pkgListUri(@Nullable HttpServletRequest request, String searchExpression) {
        UriComponentsBuilder builder = baselineUri(request).pathSegment("pkg");
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

        UriComponentsBuilder builder = baselineUri(request).pathSegment("pkg", pkgName);

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
        return baselineUri(request).pathSegment("pkg", pkgName, "changelog");
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
