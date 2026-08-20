/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import org.haiku.haikudepotserver.api2.PkgApiService;
import org.haiku.haikudepotserver.api2.model.GetPkgChangelogRequestEnvelope;
import org.haiku.haikudepotserver.api2.model.GetPkgChangelogResult;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.MenuGroup;
import org.haiku.haikudepotserver.multipage.model.NavigationDestination;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponents;

import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * <p>Renders a page containing the changelog for a package.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/pkg")
public class PkgChangelogController {

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final PkgApiService pkgApiService;
    private final MultipageNavigationService navigationService;
    private final MultipageWebResourceService multipageWebResourceService;

    public PkgChangelogController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            PkgApiService pkgApiService,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.pkgApiService = Preconditions.checkNotNull(pkgApiService);
        this.navigationService = Preconditions.checkNotNull(navigationService);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
    }

    @RequestMapping(value = "{pkgName}/changelog", method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView viewPkgChangelog(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String pkgName) {

        UriComponents viewPkgComponents = navigationService.pkgViewUri(
                httpServletRequest,
                pkgName,
                null, null, null).build();

        GetPkgChangelogRequestEnvelope request = new GetPkgChangelogRequestEnvelope();
        request.setPkgName(pkgName);

        GetPkgChangelogResult result = pkgApiService.getPkgChangelog(request);

        return new ModelAndView(
                NavigationDestination.PKG_CHANGELOG.template(),
                Map.of(
                        MultipageConstants.KEY_DATA,
                        new PkgChangelogData(
                                navigationService.deriveMenuGroups(httpServletRequest),
                                pkgName,
                                result.getContent(),
                                viewPkgComponents
                        ),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER,
                        internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES,
                        multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    public record PkgChangelogData(
            List<MenuGroup> menuGroups,

            String pkgName,
            String changelog,

            UriComponents viewUriComponents
    ) {
    }

}
