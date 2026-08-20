/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.PkgApiService;
import org.haiku.haikudepotserver.api2.model.GetPkgPkgVersion;
import org.haiku.haikudepotserver.api2.model.GetPkgRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgResult;
import org.haiku.haikudepotserver.api2.model.PkgVersionType;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.ReferenceDataRepository;
import org.haiku.haikudepotserver.multipage.model.Defaults;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponents;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.haiku.haikudepotserver.multipage.controller.BannerSearchController.SEGMENT_BANNER_SEARCH;

/**
 * <p>This controller is hit when the user queries the system using the search bar at the top of the web page.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/" + SEGMENT_BANNER_SEARCH)
public class BannerSearchController {

    protected final static Logger LOGGER = LoggerFactory.getLogger(BannerSearchController.class);

    public final static String SEGMENT_BANNER_SEARCH = "banner-search";

    // TODO; move this to some common library.
    public final static Pattern PKG_NAME_PATTERN = Pkg.PATTERN_NAME;

    private final PkgApiService pkgApiService;
    private final ReferenceDataRepository referenceDataRepository;
    private final MultipageNavigationService multipageNavigationService;

    public BannerSearchController(
            PkgApiService pkgApiService,
            ReferenceDataRepository referenceDataRepository,
            MultipageNavigationService multipageNavigationService) {
        this.pkgApiService = Preconditions.checkNotNull(pkgApiService);
        this.referenceDataRepository = Preconditions.checkNotNull(referenceDataRepository);
        this.multipageNavigationService = Preconditions.checkNotNull(multipageNavigationService);

    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView bannerSearch(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @RequestParam(value = MultipageConstants.KEY_SEARCHEXPRESSION, required = false) String searchExpression) {

        Optional<UriComponents> pkgViewUrl = trySinglePkgView(httpServletRequest, locale, searchExpression);

        if (pkgViewUrl.isPresent()) {
            return new ModelAndView(
                    "redirect:%s".formatted(pkgViewUrl.get().getPath()),
                    pkgViewUrl.get().getQueryParams()
            );
        }

        UriComponents pkgListUri = multipageNavigationService.pkgListUri(httpServletRequest, searchExpression).build();

        return new ModelAndView(
                "redirect:%s".formatted(pkgListUri.getPath()),
                pkgListUri.getQueryParams()
        );
    }

    private Optional<UriComponents> trySinglePkgView(
            HttpServletRequest httpServletRequest, Locale locale, String name) {
        if (StringUtils.isBlank(name)) {
            return Optional.empty();
        }
        if (!PKG_NAME_PATTERN.matcher(name).matches()) {
            return Optional.empty();
        }

        Defaults defaults = referenceDataRepository.getDefaults();

        GetPkgRequestEnvelope requestEnvelope = new GetPkgRequestEnvelope();
        requestEnvelope.name(name);
        requestEnvelope.setArchitectureCode(defaults.defaultArchitectureCode());
        requestEnvelope.setNaturalLanguageCode(locale.getLanguage());
        requestEnvelope.setRepositorySourceCode(defaults.defaultRepositorySourceCode());
        requestEnvelope.setVersionType(PkgVersionType.LATEST);

        try {
            GetPkgResult result = pkgApiService.getPkg(requestEnvelope);
            return createPkgViewUri(httpServletRequest, result);
        } catch (ObjectNotFoundException onfe) {
            LOGGER.info("no pkg found for search [{}]", name);
            return Optional.empty();
        }
    }

    private Optional<UriComponents> createPkgViewUri(
            HttpServletRequest httpServletRequest, GetPkgResult result) {
        GetPkgPkgVersion pkgVersion = result.getVersions().getFirst();

        return Optional.of(multipageNavigationService.pkgViewUri(
                httpServletRequest,
                result.getName(),
                pkgVersion.getRepositorySourceCode(),
                pkgVersion.getArchitectureCode(),
                new VersionCoordinates(
                        pkgVersion.getMajor(),
                        pkgVersion.getMinor(),
                        pkgVersion.getMicro(),
                        pkgVersion.getPreRelease(),
                        pkgVersion.getRevision()
                )
        ).build());
    }

}
