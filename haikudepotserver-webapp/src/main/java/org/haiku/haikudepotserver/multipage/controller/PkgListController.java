/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import jakarta.annotation.Nullable;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.PkgApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.*;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.*;
import org.haiku.haikudepotserver.multipage.model.Architecture;
import org.haiku.haikudepotserver.multipage.model.PkgCategory;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.data.DataQuantity;
import org.haiku.haikudepotserver.support.data.DataUnitHelper;
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
 * <p>Renders the page that allows the user to list the packages and perform basic search
 * into the set of packages by supplying criteria.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/pkg")
public class PkgListController {

    /**
     * <p>This defines the type of display of packages that are shown.</p>
     */

    public enum ViewCriteriaType {
        FEATURED,
        ALL,
        CATEGORIES,
        MOSTRECENT,
        MOSTVIEWED;

        public String titleKey() {
            return String.format(
                    "mp.pkg_list.filters.view_criteria_type.%s",
                    name().toLowerCase()
            );
        }

    }

    // these should correspond to the single-page keys for the pkg list page.
    public final static String KEY_REPOSITORIESCODES = "repos";
    public final static String KEY_VIEWCRITERIATYPECODE = "viewcrttyp";
    public final static String KEY_INCLUDEDEVELOPER = "incldevp";
    public final static String KEY_ONLYDESKTOP = "onlydstp";
    public final static String KEY_ONLYNATIVEDESKTOP = "onlyndtp";
    public final static String KEY_SHOWFILTERS = "filt";

    private final static int PAGESIZE = 15;

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final ReferenceDataRepository referenceDataRepository;
    private final MultipageNavigationService navigationService;
    private final PkgApiService pkgApiService;
    private final MultipageWebResourceService multipageWebResourceService;
    private final MultipageSecurityService multipageSecurityService;

    public PkgListController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            ReferenceDataRepository referenceDataRepository,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService,
            PkgApiService pkgApiService,
            MultipageSecurityService multipageSecurityService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.referenceDataRepository = referenceDataRepository;
        this.navigationService = navigationService;
        this.pkgApiService = pkgApiService;
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
        this.multipageSecurityService = Preconditions.checkNotNull(multipageSecurityService);
    }

    /**
     * <p>This is the entry point for the pkg list page. It will look at the parameters supplied and will
     * establish what should be displayed.</p>
     */

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView pkgList(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @RequestParam(value = MultipageConstants.KEY_OFFSET, defaultValue = "0") Integer offset,
            @RequestParam(value = KEY_REPOSITORIESCODES, required = false) List<String> repositoryCodes,
            @RequestParam(value = MultipageConstants.KEY_ARCHITECTURECODE, required = false) String architectureCode,
            @RequestParam(value = MultipageConstants.KEY_PKGCATEGORYCODE, required = false) String pkgCategoryCode,
            @RequestParam(value = MultipageConstants.KEY_SEARCHEXPRESSION, required = false) String searchExpression,
            @RequestParam(value = KEY_VIEWCRITERIATYPECODE, required = false) ViewCriteriaType viewCriteriaType,
            @RequestParam(value = KEY_INCLUDEDEVELOPER, required = false) Boolean includeDeveloper,
            @RequestParam(value = KEY_ONLYDESKTOP, required = false) Boolean onlyDesktop,
            @RequestParam(value = KEY_ONLYNATIVEDESKTOP, required = false) Boolean onlyNativeDesktop,
            @RequestParam(value = KEY_SHOWFILTERS, required = false) Boolean showFilters
            ) {
        return new ModelAndView(
                "multipage/pkg-list",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(
                                httpServletRequest,
                                locale,
                                offset,
                                repositoryCodes,
                                architectureCode,
                                pkgCategoryCode,
                                searchExpression,
                                viewCriteriaType,
                                BooleanUtils.isTrue(includeDeveloper),
                                BooleanUtils.isTrue(onlyDesktop),
                                BooleanUtils.isTrue(onlyNativeDesktop),
                                BooleanUtils.isTrue(showFilters)
                        ),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private PkgListData createData(
            HttpServletRequest httpServletRequest,
            Locale locale,
            int offset,
            List<String> repositoryCodes,
            String architectureCode,
            String pkgCategoryCode,
            String searchExpression,
            ViewCriteriaType viewCriteriaType,
            boolean includeDeveloper,
            boolean onlyDesktop,
            boolean onlyNativeDesktop,
            boolean showFilters
    ) {
        ReferenceData referenceData = referenceDataRepository.getReferenceData(locale.toLanguageTag());

        final String actualPkgCategoryCode = Optional.ofNullable(pkgCategoryCode)
                .map(StringUtils::trimToNull)
                .orElseGet(() -> referenceData.pkgCategories().getFirst().code());
        final String actualArchitectureCode = Optional.ofNullable(architectureCode)
                .map(StringUtils::trimToNull)
                .orElse(referenceDataRepository.getDefaults().defaultArchitectureCode());
        final List<String> actualRepositoryCodes = Optional.ofNullable(repositoryCodes)
                .filter(CollectionUtils::isNotEmpty)
                .orElse(List.of(referenceDataRepository.getDefaults().defaultRepositoryCode()));

        searchExpression = StringUtils.trimToNull(searchExpression);

        SearchPkgsRequestEnvelope searchRequest = new SearchPkgsRequestEnvelope();

        searchRequest.setOffset(offset);
        searchRequest.setLimit(PAGESIZE);
        searchRequest.setExpression(searchExpression);
        searchRequest.setExpressionType(SearchPkgsRequestEnvelope.ExpressionTypeEnum.CONTAINS);

        searchRequest.setIncludeDevelopment(includeDeveloper);
        searchRequest.setOnlyDesktop(onlyDesktop);
        searchRequest.setOnlyNativeDesktop(onlyNativeDesktop);

        searchRequest.setArchitectureCode(actualArchitectureCode);
        searchRequest.setRepositoryCodes(actualRepositoryCodes);

        searchRequest.setNaturalLanguageCode(locale.getLanguage());

        switch (null == viewCriteriaType ? ViewCriteriaType.FEATURED : viewCriteriaType) {
            case FEATURED -> searchRequest.setSortOrdering(SearchPkgsSortOrdering.PROMINENCE);
            case CATEGORIES -> {
                searchRequest.setSortOrdering(SearchPkgsSortOrdering.NAME);
                searchRequest.setPkgCategoryCode(pkgCategoryCode);
            }
            case ALL -> searchRequest.setSortOrdering(SearchPkgsSortOrdering.NAME);
            case MOSTVIEWED -> searchRequest.setSortOrdering(SearchPkgsSortOrdering.VERSIONVIEWCOUNTER);
            case MOSTRECENT -> searchRequest.setSortOrdering(SearchPkgsSortOrdering.VERSIONCREATETIMESTAMP);
            default -> throw new IllegalStateException("unhandled view criteria type");
        }

        SearchPkgsResult searchResult = pkgApiService.searchPkgs(searchRequest);

        Integer total = searchResult.getTotal();

        return new PkgListData(
                ServletUriComponentsBuilder.fromRequest(httpServletRequest).build(),
                MultipageNavigationService.stripDestination(
                        navigationService.deriveMenuGroups(httpServletRequest),
                        EnumSet.of(NavigationDestination.PKG_LIST)
                ),
                navigationService.getUserAndNavigation(httpServletRequest),
                NaturalLanguageCoordinates.fromLocale(locale),
                navigationService.pkgListUri(httpServletRequest, null).build().toUriString(),
                navigationService.createRelayParameters(httpServletRequest),
                new Criteria(
                        searchExpression,
                        referenceData,
                        ImmutableList.copyOf(ViewCriteriaType.values()),
                        referenceData.architectureForCode(actualArchitectureCode),
                        referenceData.repositoriesForCodes(actualRepositoryCodes),
                        referenceData.pkgCategoryForCode(actualPkgCategoryCode),
                        viewCriteriaType,
                        includeDeveloper,
                        onlyDesktop,
                        onlyNativeDesktop,
                        showFilters
                ),
                searchResult.getTotal(),
                searchResult.getItems().stream()
                        .map(searchPkgsPkg -> mapToPkgVersion(httpServletRequest, searchPkgsPkg))
                        .toList(),
                (null == total || 0 == total) ? null :
                        new Pagination(
                                Math.clamp(offset, 0, total - 1),
                                total,
                                PAGESIZE),
                viewCriteriaType == ViewCriteriaType.CATEGORIES
        );
    }

    private PkgVersion mapToPkgVersion(HttpServletRequest httpServletRequest, SearchPkgsPkg pkg) {
        SearchPkgsPkgVersion pkgVersion = pkg.getVersions().getLast();

        String summaryPlus = pkgVersion.getSummary();

        if (StringUtils.isNotBlank(pkgVersion.getDescriptionSnippet())) {
            summaryPlus = summaryPlus + pkgVersion.getDescriptionSnippet();
        }

        summaryPlus = StringUtils.replaceChars(summaryPlus, "\n\r", " ");

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

        return new PkgVersion(
                pkg.getName(),
                pkgVersion.getTitle(),
                summaryPlus,
                pkg.getIsNativeDesktop(),
                Optional.ofNullable(pkgVersion.getPayloadLength())
                        .map(DataUnitHelper::scaleBytesToSensibleUnit)
                        .orElse(null),
                pkg.getDerivedRating(),
                new VersionCoordinates(
                        pkgVersion.getMajor(),
                        pkgVersion.getMinor(),
                        pkgVersion.getMicro(),
                        pkgVersion.getPreRelease(),
                        pkgVersion.getRevision()
                ),
                Instant.ofEpochMilli(pkg.getModifyTimestamp()),
                pkgVersion.getViewCounter(),
                viewUri
        );
    }

    /**
     * <p>This is the data model for the page to be rendered from.</p>
     */

    public record PkgListData(
            UriComponents uriComponents,
            List<MenuGroup> menuGroups,
            UserAndNavigation userAndNavigation,

            NaturalLanguageCoordinates naturalLanguage,
            String searchUrl,
            Map<String, String> relayParameters,
            Criteria criteria,
            Integer total,
            List<PkgVersion> pkgVersions,
            Pagination pagination,
            boolean showCategoryFilter
    ) {
    }

    public record PkgVersion(
            String pkgName,
            String title,
            String summaryPlus,
            boolean isNativeDesktop,
            @Nullable DataQuantity payloadLength,
            BigDecimal derivedRating,
            VersionCoordinates version,
            Instant versionCreateTimestamp,
            Long viewCounter,
            UriComponents viewUriComponents
    ) {
    }

    public record Criteria(
            String searchExpression,
            ReferenceData referenceData,
            List<ViewCriteriaType> viewCriteriaTypes,
            Architecture architecture,
            List<Repository> repositories,
            PkgCategory pkgCategory,
            ViewCriteriaType viewCriteriaType,
            boolean includeDeveloper,
            boolean onlyDesktop,
            boolean onlyNativeDesktop,
            boolean showFilters
    ) {
    }

}
