/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.CaseFormat;
import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.MiscellaneousApiService;
import org.haiku.haikudepotserver.api2.model.GetAllContributorsResult;
import org.haiku.haikudepotserver.api2.model.GetRuntimeInformationResult;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageSecurityService;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.*;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import java.util.*;

/**
 * <p>Renders a page about the HaikuDepotServer system.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + AboutController.SEGMENT_ABOUT)
public class AboutController {

    public final static String SEGMENT_ABOUT = "about";

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final MiscellaneousApiService miscellaneousApiService;
    private final MultipageNavigationService navigationService;
    private final MultipageWebResourceService multipageWebResourceService;
    private final MultipageSecurityService multipageSecurityService;

    public AboutController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            MiscellaneousApiService miscellaneousApiService,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService,
            MultipageSecurityService multipageSecurityService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.miscellaneousApiService = Preconditions.checkNotNull(miscellaneousApiService);
        this.navigationService = Preconditions.checkNotNull(navigationService);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
        this.multipageSecurityService = Preconditions.checkNotNull(multipageSecurityService);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView about(
            HttpServletRequest httpServletRequest,
            Locale locale
    ) {
        InternationalizationSupplier internationalizationSupplier = internationalizationSupplierFactory.create(locale);

        return new ModelAndView(
                "multipage/about",
                Map.of(
                       MultipageConstants.KEY_DATA, createData(
                                httpServletRequest,
                                internationalizationSupplier
                        ),
                       MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplier,
                       MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private AboutData createData(
            HttpServletRequest httpServletRequest,
            InternationalizationSupplier internationalizationSupplier) {
        return new AboutData(
                MultipageNavigationService.stripDestination(
                        navigationService.deriveMenuGroups(httpServletRequest),
                        EnumSet.of(NavigationDestination.ABOUT)
                ),
                navigationService.getUserAndNavigation(httpServletRequest),
                getContributors(internationalizationSupplier),
                getRuntimeInformation()
        );
    }

    private RuntimeInformation getRuntimeInformation() {
        GetRuntimeInformationResult runtimeInformationResult = miscellaneousApiService.getRuntimeInformation();
        return new RuntimeInformation(runtimeInformationResult.getProjectVersion());
    }

    private List<Contributor> getContributors(InternationalizationSupplier internationalizationSupplier) {
        GetAllContributorsResult result = miscellaneousApiService.getAllContributors();
        return result.getContributors().stream()
                .map(c -> new Contributor(
                        ContributorType.valueOf(c.getType().name()),
                        c.getName(),
                        Optional.ofNullable(c.getNaturalLanguageCode())
                                .filter(StringUtils::isNotBlank)
                                .map(nlc -> nlc.replace("_", "-")) // TODO; normalize the keys
                                .map(nlc -> internationalizationSupplier.getMessage("naturalLanguage.%s".formatted(nlc)))
                                .orElse(null)
                ))
                .sorted(Comparator
                        .comparing(Contributor::type)
                        .thenComparing(c -> Optional.ofNullable(c.languageTitle()).orElse(""))
                        .thenComparing(Contributor::name))
                .toList();
    }

    public enum ContributorType {
        ENGINEERING,
        LOCALIZATION;

        public String getTitleKey() {
            return "mp.about.contributors.type.%s.title".formatted(
                    CaseFormat.UPPER_UNDERSCORE.to(CaseFormat.LOWER_UNDERSCORE, name())
            );
        }
    }

    public record AboutData(
            List<MenuGroup> menuGroups,
            UserAndNavigation userAndNavigation,

            List<Contributor> contributors,
            RuntimeInformation runtimeInformation
    ) {
    }

    public record RuntimeInformation(
       String projectVersion
    ) {
    }

    public record Contributor(
            ContributorType type,
            String name,
            @Nullable String languageTitle
    ) {
    }

}
