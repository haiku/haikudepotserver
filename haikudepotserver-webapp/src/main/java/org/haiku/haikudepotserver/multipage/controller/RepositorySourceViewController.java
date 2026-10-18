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

import java.time.Instant;
import java.util.*;

/**
 * <p>This controller takes responsibility for displaying all the details about a Repository.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + MultipageConstants.SEGMENT_REPOSITORY_SOURCE)
public class RepositorySourceViewController {

    private static final Logger LOGGER = LoggerFactory.getLogger(RepositorySourceViewController.class);

    private static final String SEGMENT_ACTION = "action";

    public enum OneshotAction {
        DEACTIVATE,
        REACTIVATE,
        TRIGGER_IMPORT
    }

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final MultipageNavigationService navigationService;
    private final RepositoryApiService repositoryApiService;
    private final MultipageSecurityService multipageSecurityService;
    private final MultipageWebResourceService multipageWebResourceService;

    public RepositorySourceViewController(
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

    @RequestMapping(value = "{repositorySourceCode}", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView view(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositorySourceCode
    ) {
        return goViewRepositorySource(httpServletRequest, locale, repositorySourceCode, false);
    }

    /**
     * <p>This completes a request to perform some action on a package such as "enable", "disable" etc...</p>
     */
    @RequestMapping(
            value = "{repositorySourceCode}/" + SEGMENT_ACTION,
            method = RequestMethod.POST,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE,
            produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView oneshotAction(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositorySourceCode,
            @ModelAttribute OneshotActionData oneshotActionData) {
        Preconditions.checkArgument(null != oneshotActionData, "the action is required.");

        boolean didTriggerImport = false;

        switch (oneshotActionData.action()) {
            case DEACTIVATE -> repositoryApiService.updateRepositorySource(new UpdateRepositorySourceRequestEnvelope()
                    .code(repositorySourceCode)
                    .active(false)
                    .addFilterItem(UpdateRepositorySourceFilter.ACTIVE));
            case REACTIVATE -> repositoryApiService.updateRepositorySource(new UpdateRepositorySourceRequestEnvelope()
                    .code(repositorySourceCode)
                    .active(true)
                    .addFilterItem(UpdateRepositorySourceFilter.ACTIVE));
            case TRIGGER_IMPORT -> {
                repositoryApiService.triggerImportRepository(new TriggerImportRepositoryRequestEnvelope()
                        .repositorySourceCodes(List.of(repositorySourceCode)));
                didTriggerImport = true;
            }
        }

        LOGGER.info("did perform oneshot action [{}] for repository source [{}]", oneshotActionData.action(), repositorySourceCode);

        return goViewRepositorySource(httpServletRequest, locale, repositorySourceCode, didTriggerImport);
    }

    private ModelAndView goViewRepositorySource(
            HttpServletRequest httpServletRequest,
            Locale locale,
            String repositorySourceCode,
            boolean didTriggerImport) {
        return new ModelAndView(
                "multipage/repository-source-view",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, repositorySourceCode, didTriggerImport),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private RepositorySourceViewData createData(
            HttpServletRequest httpServletRequest,
            String repositorySourceCode,
            boolean didTriggerImport) {

        GetRepositorySourceResult result = repositoryApiService.getRepositorySource(
                new GetRepositorySourceRequestEnvelope()
                        .code(repositorySourceCode));

        Set<Permission> permissions = multipageSecurityService.filterPermissionsForRepository(
                result.getRepositoryCode(), Set.of(Permission.REPOSITORY_EDIT, Permission.REPOSITORY_IMPORT));

        UriComponents uriComponents = ServletUriComponentsBuilder.fromRequest(httpServletRequest).build();
        UriComponents oneshotActionUriComponents = navigationService.repositorySourceViewUri(httpServletRequest, repositorySourceCode)
                .pathSegment(SEGMENT_ACTION).build();

        return new RepositorySourceViewData(
                uriComponents,
                navigationService.deriveMenuGroups(httpServletRequest),
                navigationService.getUserAndNavigation(httpServletRequest),

                result.getCode(),
                BooleanUtils.isTrue(result.getActive()),
                result.getIdentifier(),
                result.getArchitectureCode(),
                Optional.ofNullable(result.getLastImportTimestamp()).map(Instant::ofEpochMilli).orElse(null),
                result.getForcedInternalBaseUrl(),
                result.getExpectedUpdateFrequencyHours(),
                result.getRepositoryCode(),
                navigationService.repositoryViewUri(httpServletRequest, result.getRepositoryCode()).build(),

                permissions.contains(Permission.REPOSITORY_EDIT),
                permissions.contains(Permission.REPOSITORY_IMPORT),

                oneshotActionUriComponents,

                didTriggerImport,

                result.getExtraIdentifiers(),

                result.getRepositorySourceMirrors().stream()
                        .map(rsm -> new RepositorySourceMirror(
                                BooleanUtils.isTrue(rsm.getActive()),
                                BooleanUtils.isTrue(rsm.getIsPrimary()),
                                rsm.getCountryCode(),
                                rsm.getBaseUrl()
                        ))
                        .toList()
        );
    }

    /**
     * A model for the data which is displayed on the page.
     *
     * @param canEdit {@code true} if the authenticated user can edit the repository.
     */

    public record RepositorySourceViewData(
            @NotNull UriComponents uriComponents,
            @NotNull List<MenuGroup> menuGroups,
            @NotNull UserAndNavigation userAndNavigation,

            @NotNull String code,
            boolean active,
            @Nullable String identifier,
            @Nullable String architectureCode,
            Instant lastImportTimestamp,
            @Nullable String forcedInternalBaseUrl,
            Integer expectedUpdateFrequencyHours,
            String repositoryCode,
            UriComponents viewRepositoryUriComponents,

            boolean canEdit,
            boolean canImport,

            @NotNull UriComponents oneshotActionUriComponents,

            boolean didTriggerImport,

            List<String> extraIdentifiers,
            List<RepositorySourceMirror> repositorySourceMirrors
    ) {
    }

    public record RepositorySourceMirror(
            boolean active,
            boolean isPrimary,
            String countryCode,
            String baseUrl
    ) {
    }

    public record OneshotActionData(
            @NotNull OneshotAction action) {
    }

}
