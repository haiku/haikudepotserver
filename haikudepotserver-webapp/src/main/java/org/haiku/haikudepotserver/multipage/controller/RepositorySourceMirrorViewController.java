/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.BooleanUtils;
import org.haiku.haikudepotserver.api2.RepositoryApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.multipage.MultipageSecurityService;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.MenuGroup;
import org.haiku.haikudepotserver.multipage.model.UserAndNavigation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * <p>This controller takes responsibility for displaying all the details about a
 * RepositorySourceMirror.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + MultipageConstants.SEGMENT_REPOSITORY_SOURCE_MIRROR)
public class RepositorySourceMirrorViewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositorySourceMirrorViewController.class);

    private static final String SEGMENT_ACTION = "action";

    public enum OneshotAction {
        SET_PRIMARY,
        DEACTIVATE,
        REACTIVATE,
        REMOVE
    }

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final MultipageNavigationService navigationService;
    private final RepositoryApiService repositoryApiService;
    private final MultipageSecurityService multipageSecurityService;
    private final MultipageWebResourceService multipageWebResourceService;

    public RepositorySourceMirrorViewController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            MultipageNavigationService navigationService,
            RepositoryApiService repositoryApiService,
            MultipageSecurityService multipageSecurityService,
            MultipageWebResourceService multipageWebResourceService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.navigationService = Preconditions.checkNotNull(navigationService);
        this.repositoryApiService = Preconditions.checkNotNull(repositoryApiService);
        this.multipageSecurityService = Preconditions.checkNotNull(multipageSecurityService);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
    }

    @RequestMapping(value = "{repositorySourceMirrorCode}", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView view(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositorySourceMirrorCode
    ) {
        return goViewRepositorySourceMirror(httpServletRequest, locale, repositorySourceMirrorCode);
    }

    /**
     * <p>This completes a request to perform some action on a package such as "enable", "disable" etc...</p>
     */
    @RequestMapping(
            value = "{repositorySourceMirrorCode}/" + SEGMENT_ACTION,
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView oneshotAction(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositorySourceMirrorCode,
            @ModelAttribute OneshotActionData oneshotActionData) {
        Preconditions.checkArgument(null != oneshotActionData, "the action is required.");

        switch (oneshotActionData.action()) {
            case DEACTIVATE -> repositoryApiService.updateRepositorySourceMirror(new UpdateRepositorySourceMirrorRequestEnvelope()
                    .code(repositorySourceMirrorCode)
                    .active(false)
                    .addFilterItem(UpdateRepositorySourceMirrorFilter.ACTIVE));
            case REACTIVATE -> repositoryApiService.updateRepositorySourceMirror(new UpdateRepositorySourceMirrorRequestEnvelope()
                    .code(repositorySourceMirrorCode)
                    .active(true)
                    .addFilterItem(UpdateRepositorySourceMirrorFilter.ACTIVE));
            case SET_PRIMARY -> repositoryApiService.updateRepositorySourceMirror(new UpdateRepositorySourceMirrorRequestEnvelope()
                    .code(repositorySourceMirrorCode)
                    .isPrimary(true)
                    .addFilterItem(UpdateRepositorySourceMirrorFilter.IS_PRIMARY));
            case REMOVE -> {
                return goDelete(httpServletRequest, repositorySourceMirrorCode);
            }
        }

        LOGGER.info("did perform oneshot action [{}] for repository source mirror [{}]",
                oneshotActionData.action(), repositorySourceMirrorCode);

        return goViewRepositorySourceMirror(httpServletRequest, locale, repositorySourceMirrorCode);
    }

    private ModelAndView goViewRepositorySourceMirror(
            HttpServletRequest httpServletRequest,
            Locale locale,
            String repositorySourceCode) {
        return new ModelAndView(
                "multipage/repository-source-mirror-view",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, repositorySourceCode),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private ModelAndView goDelete(HttpServletRequest httpServletRequest, String repositorySourceMirrorCode) {
        repositoryApiService.removeRepositorySourceMirror(new RemoveRepositorySourceMirrorRequestEnvelope()
                .code(repositorySourceMirrorCode));

        GetRepositorySourceMirrorResult result = repositoryApiService.getRepositorySourceMirror(
                new GetRepositorySourceMirrorRequestEnvelope()
                        .code(repositorySourceMirrorCode));

        return new ModelAndView(
                "redirect:%s".formatted(navigationService.repositorySourceViewUri(
                        httpServletRequest, result.getRepositorySourceCode())),
                Map.of()
        );
    }

    private RepositorySourceMirrorViewData createData(
            HttpServletRequest httpServletRequest,
            String repositorySourceMirrorCode) {

        GetRepositorySourceMirrorResult result = repositoryApiService.getRepositorySourceMirror(
                new GetRepositorySourceMirrorRequestEnvelope()
                        .code(repositorySourceMirrorCode));

        GetRepositorySourceResult repositorySourceResult = repositoryApiService.getRepositorySource(
                new GetRepositorySourceRequestEnvelope()
                        .code(result.getRepositorySourceCode()));

        Set<Permission> permissions = multipageSecurityService.filterPermissionsForRepository(
                repositorySourceResult.getRepositoryCode(), Set.of(Permission.REPOSITORY_EDIT));

        UriComponents uriComponents = ServletUriComponentsBuilder.fromRequest(httpServletRequest).build();
        UriComponents oneshotActionUriComponents = navigationService
                .repositorySourceMirrorViewUri(httpServletRequest, repositorySourceMirrorCode)
                .pathSegment(SEGMENT_ACTION).build();

        return new RepositorySourceMirrorViewData(
                uriComponents,
                navigationService.deriveMenuGroups(httpServletRequest),
                navigationService.getUserAndNavigation(httpServletRequest),

                BooleanUtils.isTrue(result.getIsPrimary()),
                BooleanUtils.isTrue(result.getActive()),
                result.getCountryCode(),
                result.getBaseUrl(),
                result.getDescription(),
                result.getRepositorySourceCode(),
                repositorySourceResult.getRepositoryCode(),

                permissions.contains(Permission.REPOSITORY_EDIT),

                oneshotActionUriComponents,
                navigationService.repositoryViewUri(httpServletRequest, repositorySourceResult.getRepositoryCode()).build(),
                navigationService.repositorySourceViewUri(httpServletRequest, repositorySourceResult.getCode()).build(),
                navigationService.repositorySourceMirrorEditUri(httpServletRequest, result.getCode()).build()
        );
    }

    /**
     * A model for the data which is displayed on the page.
     * @param canEdit {@code true} if the authenticated user can edit the repository.
     */

    public record RepositorySourceMirrorViewData(
            @NotNull UriComponents uriComponents,
            @NotNull List<MenuGroup> menuGroups,
            @NotNull UserAndNavigation userAndNavigation,

            boolean isPrimary,
            boolean active,
            String countryCode,
            String baseUrl,
            String description,

            String repositorySourceCode,
            String repositoryCode,

            boolean canEdit,

            @NotNull UriComponents oneshotActionUriComponents,
            @NotNull UriComponents viewRepositoryUriComponents,
            @NotNull UriComponents viewRepositorySourceUriComponents,
            @NotNull UriComponents editUriComponents
    ) {
    }

    public record OneshotActionData(
            @NotNull OneshotAction action) {
    }

}
