/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.*;

/**
 * <p>This controller takes responsibility for displaying all the details about a Repository.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + MultipageConstants.SEGMENT_REPOSITORY)
public class RepositoryViewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositoryViewController.class);

    private static final String SEGMENT_ACTION = "action";

    public enum OneshotAction {
        DELETE_PASSWORD,
        DEACTIVATE,
        REACTIVATE,
        TRIGGER_IMPORT
    }

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final MultipageNavigationService navigationService;
    private final RepositoryApiService repositoryApiService;
    private final MultipageSecurityService multipageSecurityService;
    private final MultipageWebResourceService multipageWebResourceService;

    public RepositoryViewController(
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

    @RequestMapping(value = "{repositoryCode}", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView viewPkg(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositoryCode
    ) {
        return goViewPkg(httpServletRequest, locale, repositoryCode, false);
    }

    /**
     * <p>This completes a request to perform some action on a package such as "enable", "disable" etc...</p>
     */
    @RequestMapping(
            value = "{repositoryCode}/" + SEGMENT_ACTION,
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView oneshotActionPkg(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositoryCode,
            @ModelAttribute OneshotActionData oneshotActionData
    ) {
        Preconditions.checkArgument(null != oneshotActionData, "the action is required.");

        boolean didTriggerImport = false;

        switch (oneshotActionData.action()) {
            case DEACTIVATE -> repositoryApiService.updateRepository(new UpdateRepositoryRequestEnvelope()
                    .code(repositoryCode)
                    .active(false)
                    .addFilterItem(UpdateRepositoryFilter.ACTIVE));
            case REACTIVATE -> repositoryApiService.updateRepository(new UpdateRepositoryRequestEnvelope()
                    .code(repositoryCode)
                    .active(true)
                    .addFilterItem(UpdateRepositoryFilter.ACTIVE));
            case TRIGGER_IMPORT -> {
                repositoryApiService.triggerImportRepository(new TriggerImportRepositoryRequestEnvelope()
                    .repositoryCode(repositoryCode));
                didTriggerImport = true;
            }
            case DELETE_PASSWORD -> repositoryApiService.updateRepository(new UpdateRepositoryRequestEnvelope()
                    .code(repositoryCode)
                    .passwordClear(null)
                    .addFilterItem(UpdateRepositoryFilter.PASSWORD));
        }

        LOGGER.info("did perform oneshot action [{}] for repository [{}]", oneshotActionData.action(), repositoryCode);

        return goViewPkg(httpServletRequest, locale, repositoryCode, didTriggerImport);
    }

    private ModelAndView goViewPkg(
            HttpServletRequest httpServletRequest,
            Locale locale,
            String repositoryCode,
            boolean didTriggerImport) {
        return new ModelAndView(
                "multipage/repository-view",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, repositoryCode, didTriggerImport),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private RepositoryViewData createData(
            HttpServletRequest httpServletRequest,
            String repositoryCode,
            boolean didTriggerImport) {

        GetRepositoryResult result = repositoryApiService.getRepository(
                new GetRepositoryRequestEnvelope()
                        .code(repositoryCode));

        Set<Permission> permissions = multipageSecurityService.filterPermissionsForRepository(
                repositoryCode, Set.of(Permission.REPOSITORY_EDIT, Permission.REPOSITORY_IMPORT));

        UriComponents uriComponents = ServletUriComponentsBuilder.fromRequest(httpServletRequest).build();
        UriComponents oneshotActionUriComponents = navigationService.repositoryViewUri(httpServletRequest, repositoryCode)
                        .pathSegment(SEGMENT_ACTION).build();

        return new RepositoryViewData(
                uriComponents,
                navigationService.deriveMenuGroups(httpServletRequest),
                navigationService.getUserAndNavigation(httpServletRequest),

                result.getCode(),
                result.getName(),
                BooleanUtils.isTrue(result.getActive()),
                BooleanUtils.isTrue(result.getHasPassword()),
                Optional.ofNullable(result.getInformationUrl())
                        .map(UriComponentsBuilder::fromUriString)
                        .map(UriComponentsBuilder::build)
                        .orElse(null),
                Instant.ofEpochMilli(result.getCreateTimestamp()),
                Instant.ofEpochMilli(result.getModifyTimestamp()),

                permissions.contains(Permission.REPOSITORY_EDIT),
                permissions.contains(Permission.REPOSITORY_IMPORT),

                oneshotActionUriComponents,

                didTriggerImport,

                result.getRepositorySources().stream()
                        .map(rs -> new RepositorySource(
                                rs.getActive(),
                                rs.getCode(),
                                Optional.ofNullable(rs.getLastImportTimestamp())
                                        .map(Instant::ofEpochMilli)
                                        .orElse(null)
                        ))
                        .toList()
        );
    }

    /**
     * A model for the data which is displayed on the page.
     *
     * @param canEdit {@code true} if the authenticated user can edit the repository.
     */

    public record RepositoryViewData(
            @NotNull UriComponents uriComponents,
            @NotNull List<MenuGroup> menuGroups,
            @NotNull UserAndNavigation userAndNavigation,

            @NotNull String code,
            String name,
            boolean active,
            boolean hasPassword,
            @Nullable UriComponents informationUriComponents,
            Instant createTimestamp,
            Instant modifyTimestamp,

            boolean canEdit,
            boolean canImport,

            @NotNull UriComponents oneshotActionUriComponents,

            boolean didTriggerImport,

            List<RepositorySource> repositorySources
    ) {
    }

    public record RepositorySource(
            boolean active,
            String code,
            Instant lastImportTimestamp
    ) {
    }

    public record OneshotActionData(
            @NotNull OneshotAction action) {
    }

}
