/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.PkgApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.ReferenceDataRepository;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.*;
import org.haiku.haikudepotserver.multipage.model.PkgCategory;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * <p>Renders the home page for the HaikuDepotSever system. It is a splash page about
 * the packages.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE)
public class HomeController {

    private final static int PAGESIZE = 15;

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final ReferenceDataRepository referenceDataRepository;
    private final MultipageNavigationService navigationService;
    private final PkgApiService pkgApiService;
    private final HttpServletRequest httpServletRequest;
    private final MultipageWebResourceService multipageWebResourceService;

    public HomeController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            ReferenceDataRepository referenceDataRepository,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService,
            PkgApiService pkgApiService, HttpServletRequest httpServletRequest) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.referenceDataRepository = referenceDataRepository;
        this.navigationService = navigationService;
        this.pkgApiService = pkgApiService;
        this.httpServletRequest = httpServletRequest;
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView home(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @RequestParam(value = MultipageConstants.KEY_OFFSET, defaultValue = "0") Integer offset,
            @RequestParam(value = MultipageConstants.KEY_PKGCATEGORYCODE, required = false) String pkgCategoryCode
    ) {
        return new ModelAndView(NavigationDestination.HOME.template(),
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, locale, offset, pkgCategoryCode),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private HomeData createData(
            HttpServletRequest httpServletRequest,
            Locale locale,
            Integer offset,
            String pkgCategoryCode
    ) {
        ReferenceData referenceData = referenceDataRepository.getReferenceData(locale.toLanguageTag());

        final String actualPkgCategoryCode = Optional.ofNullable(pkgCategoryCode)
                .map(StringUtils::trimToNull)
                .orElse(null);

        SearchPkgsRequestEnvelope searchRequest = new SearchPkgsRequestEnvelope();

        searchRequest.setOffset(offset);
        searchRequest.setLimit(PAGESIZE);
        searchRequest.setIncludeDevelopment(false);
        searchRequest.setOnlyDesktop(true);
        searchRequest.setArchitectureCode(referenceDataRepository.getDefaults().defaultArchitectureCode());
        searchRequest.setRepositoryCodes(List.of(referenceDataRepository.getDefaults().defaultRepositoryCode()));
        searchRequest.setNaturalLanguageCode(locale.getLanguage());
        searchRequest.setSortOrdering(SearchPkgsSortOrdering.PROMINENCE);

        if (StringUtils.isNotBlank(actualPkgCategoryCode)) {
            searchRequest.setPkgCategoryCode(referenceData.pkgCategoryForCode(actualPkgCategoryCode).code());
        }

        SearchPkgsResult searchResult = pkgApiService.searchPkgs(searchRequest);

        Integer total = searchResult.getTotal();

        return new HomeData(
                ServletUriComponentsBuilder.fromRequest(httpServletRequest).build(),
                MultipageNavigationService.stripDestination(
                        navigationService.deriveMenuGroups(httpServletRequest),
                        EnumSet.of(NavigationDestination.HOME)
                ),

                NaturalLanguageCoordinates.fromLocale(locale),
                navigationService.homeUri(httpServletRequest).build().toUriString(),
                navigationService.createRelayParameters(httpServletRequest),
                new Criteria(
                        referenceData,
                        Optional.ofNullable(actualPkgCategoryCode)
                                .map(referenceData::pkgCategoryForCode)
                                .orElse(null)
                ),
                searchResult.getItems().stream().map(this::mapToPkgVersion).toList(),
                (null == total || 0 == total) ? null :
                        new Pagination(
                                Math.clamp(offset, 0, total - 1),
                                total,
                                PAGESIZE)
        );
    }

    private HomeController.PkgVersion mapToPkgVersion(SearchPkgsPkg pkg) {
        SearchPkgsPkgVersion pkgVersion = pkg.getVersions().getLast();
        UriComponents viewUri = navigationService.pkgViewUri(
                httpServletRequest,
                pkg.getName(),
                pkgVersion.getRepositorySourceCode(),
                pkgVersion.getArchitectureCode(),
                new VersionCoordinates(
                        pkgVersion.getMajor(),
                        pkgVersion.getMinor(),
                        pkgVersion.getMicro(),
                        pkgVersion.getPreRelease(),
                        pkgVersion.getRevision()
                ))
                .build();

        return new HomeController.PkgVersion(
                pkg.getName(),
                pkgVersion.getTitle(),
                pkgVersion.getSummary(),
                viewUri,
                pkg.getIsNativeDesktop(),
                pkg.getDerivedRating(),
                Instant.ofEpochMilli(pkg.getModifyTimestamp())
        );
    }


    public record HomeData(
            UriComponents uriComponents,
            List<MenuGroup> menuGroups,

            NaturalLanguageCoordinates naturalLanguage,
            String searchUrl,
            Map<String, String> relayParameters,
            Criteria criteria,
            List<HomeController.PkgVersion> pkgVersions,
            Pagination pagination
    ) {
    }

    public record Criteria(
            ReferenceData referenceData,
            @Nullable PkgCategory pkgCategory
    ) {
    }

    public record PkgVersion(
            String pkgName,
            String title,
            String summary,
            UriComponents viewUriComponents,
            boolean isNativeDesktop,
            BigDecimal derivedRating,
            Instant versionCreateTimestamp
    ) {
    }

}
