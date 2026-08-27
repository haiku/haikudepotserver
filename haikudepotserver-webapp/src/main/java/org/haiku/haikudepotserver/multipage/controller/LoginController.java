/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplier;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;
import java.util.Map;

/**
 * <p>Renders a page for a user to login </p>
 */

@Controller
@RequestMapping(WebConstants.SEGMENT_SECURITY + "/" + WebConstants.SEGMENT_LOGIN)
public class LoginController {

    public final static String KEY_ERROR = "error";
    public final static String KEY_LOGOUT = "logout";

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final MultipageWebResourceService multipageWebResourceService;
    private final MultipageNavigationService multipageNavigationService;

    public LoginController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            MultipageWebResourceService multipageWebResourceService, MultipageNavigationService multipageNavigationService) {
        this.internationalizationSupplierFactory = Preconditions.checkNotNull(internationalizationSupplierFactory);
        this.multipageWebResourceService = Preconditions.checkNotNull(multipageWebResourceService);
        this.multipageNavigationService = multipageNavigationService;
    }

    @RequestMapping(method = RequestMethod.GET, produces = MediaType.TEXT_HTML_VALUE)
    public ModelAndView login(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @RequestParam(value = KEY_ERROR, required = false) String error,
            @RequestParam(value = KEY_LOGOUT, required = false) String logout,
            @RequestParam(value = WebConstants.KEY_REDIRECT_URI, required = false) String redirectUri
            ) {
        InternationalizationSupplier internationalizationSupplier = internationalizationSupplierFactory.create(locale);

        return new ModelAndView(
                "multipage/login",
                Map.of(
                        MultipageConstants.KEY_DATA, createData(httpServletRequest, null != error, null != logout, redirectUri),
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplier,
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private LoginData createData(HttpServletRequest request, boolean didError, boolean didLogout, String redirectUri) {
        return new LoginData(
                "/%s/%s".formatted(WebConstants.SEGMENT_SECURITY, WebConstants.SEGMENT_LOGIN_PROCESSING),
                didError,
                didLogout,
                StringUtils.trimToNull(redirectUri),
                UriComponentsBuilder
                        .fromUriString("/#!/users/add?bcguid=%s".formatted(RandomStringUtils.insecure().nextAlphabetic(5)))
                        .build(),
                UriComponentsBuilder
                        .fromUriString("/#!/initiatepasswordreset?bcguid=%s".formatted(RandomStringUtils.insecure().nextAlphabetic(5)))
                        .build(),
                multipageNavigationService.userUsageConditionsUri(null).build()
        );
    }

    public record LoginData(
            String loginUrl,
            boolean didError,
            boolean didLogout,
            String redirectUri,
            UriComponents createUserUriComponents,
            UriComponents forgotPasswordUriComponents,
            UriComponents userUsageConditionsUriComponents
    ) {
    }

}
