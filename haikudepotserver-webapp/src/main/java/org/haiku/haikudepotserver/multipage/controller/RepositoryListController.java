/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.haiku.haikudepotserver.api2.RepositoryApiService;
import org.haiku.haikudepotserver.api2.model.Permission;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.SearchRepositoriesResult;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.multipage.MultipageSecurityService;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.MenuGroup;
import org.haiku.haikudepotserver.multipage.model.Pagination;
import org.haiku.haikudepotserver.multipage.model.UserAndNavigation;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + MultipageConstants.SEGMENT_REPOSITORY)
public class RepositoryListController {

    private final static int PAGESIZE = 15;

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final MultipageNavigationService navigationService;
    private final RepositoryApiService repositoryApiService;
    private final MultipageWebResourceService multipageWebResourceService;
    private final MultipageSecurityService multipageSecurityService;

    public RepositoryListController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            MultipageNavigationService navigationService,
            RepositoryApiService repositoryApiService,
            MultipageWebResourceService multipageWebResourceService,
            MultipageSecurityService multipageSecurityService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.navigationService = Preconditions.checkNotNull(navigationService);
        this.repositoryApiService = Preconditions.checkNotNull(repositoryApiService);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
        this.multipageSecurityService = Preconditions.checkNotNull(multipageSecurityService);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView pkgList(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @RequestParam(value = MultipageConstants.KEY_OFFSET, defaultValue = "0") Integer offset,
            @RequestParam(value = MultipageConstants.KEY_INCLUDE_INACTIVE, required = false) Boolean includeInactive
    ) {
        return new ModelAndView(
                "multipage/repository-list",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, offset, BooleanUtils.isTrue(includeInactive)),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private RepositoryListData createData(HttpServletRequest httpServletRequest, int offset, boolean includeInactive) {
        Preconditions.checkArgument(offset >= 0, "bad offset");
        SearchRepositoriesResult result = repositoryApiService.searchRepositories(
                new SearchRepositoriesRequestEnvelope()
                        .includeInactive(includeInactive)
                        .limit(PAGESIZE)
                        .offset(offset)
        );

        Integer total = result.getTotal();

        UriComponents uriComponents = ServletUriComponentsBuilder.fromRequest(httpServletRequest).build();
        UriComponents includeInactiveUriComponents = navigationService.repositoryListUri(httpServletRequest)
                .replaceQueryParam(MultipageConstants.KEY_INCLUDE_INACTIVE, "true")
                .build();

        Set<Permission> permissions = multipageSecurityService.filterPermissions(
                Set.of(Permission.REPOSITORY_LIST_INACTIVE));

        return new RepositoryListData(
                uriComponents,
                navigationService.deriveMenuGroups(httpServletRequest),
                navigationService.getUserAndNavigation(httpServletRequest),
                total,
                CollectionUtils.emptyIfNull(result.getItems())
                        .stream()
                        .map(i -> new Repository(
                                BooleanUtils.isTrue(i.getActive()),
                                i.getCode(),
                                i.getName(),
                                navigationService.repositoryViewUri(httpServletRequest, i.getCode()).build()
                        )).toList(),
                (null == total || 0 == total) ? null :
                        new Pagination(
                                Math.clamp(offset, 0, total - 1),
                                total,
                                PAGESIZE),
                permissions.contains(Permission.REPOSITORY_LIST_INACTIVE),
                includeInactive,
                includeInactiveUriComponents
        );
    }

    public record RepositoryListData(
            UriComponents uriComponents,
            List<MenuGroup> menuGroups,
            UserAndNavigation userAndNavigation,

            Integer total,
            List<Repository> repositories,
            Pagination pagination,

            boolean canListInactive,

            boolean includeInactive,

            UriComponents includeInactiveUriComponents
    ) {
    }

    public record Repository(
            boolean active,
            String code,
            String name,
            UriComponents viewUriComponents
    ) {
    }

}
