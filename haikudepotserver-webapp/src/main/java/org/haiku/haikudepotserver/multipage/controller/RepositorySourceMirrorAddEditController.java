package org.haiku.haikudepotserver.multipage.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.api2.RepositoryApiService;
import org.haiku.haikudepotserver.api2.model.*;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageNavigationService;
import org.haiku.haikudepotserver.multipage.MultipageWebResourceService;
import org.haiku.haikudepotserver.multipage.ReferenceDataRepository;
import org.haiku.haikudepotserver.multipage.internationalization.InternationalizationSupplierFactory;
import org.haiku.haikudepotserver.multipage.model.Country;
import org.haiku.haikudepotserver.multipage.model.MenuGroup;
import org.haiku.haikudepotserver.multipage.model.MultipageDomain;
import org.haiku.haikudepotserver.multipage.model.UserAndNavigation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.Serial;
import java.io.Serializable;
import java.util.*;

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE)
public class RepositorySourceMirrorAddEditController {

    private final static Logger LOGGER = LoggerFactory.getLogger(RepositorySourceMirrorAddEditController.class);

    public final static String SEGMENT_ADD_MIRROR = "add_mirror";
    public final static String SEGMENT_EDIT = "edit";

    private final InternationalizationSupplierFactory internationalizationSupplierFactory;
    private final RepositoryApiService repositoryApiService;
    private final ReferenceDataRepository referenceDataRepository;
    private final MultipageNavigationService navigationService;
    private final MultipageWebResourceService multipageWebResourceService;

    public RepositorySourceMirrorAddEditController(
            InternationalizationSupplierFactory internationalizationSupplierFactory,
            RepositoryApiService repositoryApiService,
            ReferenceDataRepository referenceDataRepository,
            MultipageNavigationService navigationService,
            MultipageWebResourceService multipageWebResourceService) {
        this.internationalizationSupplierFactory = internationalizationSupplierFactory;
        this.repositoryApiService = repositoryApiService;
        this.referenceDataRepository = referenceDataRepository;
        this.navigationService = navigationService;
        this.multipageWebResourceService = multipageWebResourceService;
    }

    /**
     * This endpoint is the entry point to the add flow for a new Repository Source Mirror.
     */
    @RequestMapping(
            value = MultipageConstants.SEGMENT_REPOSITORY_SOURCE + "/{repositorySourceCode}/" + SEGMENT_ADD_MIRROR,
            method = RequestMethod.GET,
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ModelAndView addEntry(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositorySourceCode
    ) {
        RepositorySourceMirrorAddEditSessionData sessionData = new RepositorySourceMirrorAddEditSessionData(
                UUID.randomUUID().toString(),
                repositorySourceCode,
                null);

        httpServletRequest.getSession(true).setAttribute(
                MultipageDomain.REPOSITORY_SOURCE_MIRROR_ADD_EDIT.getAttributeName(),
                        sessionData);

        RepositorySourceMirrorAddEditData data = new RepositorySourceMirrorAddEditData(
                ServletUriComponentsBuilder.fromRequest(httpServletRequest).build(),
                navigationService.deriveMenuGroups(httpServletRequest),
                navigationService.getUserAndNavigation(httpServletRequest),

                sessionData.code(),
                null,
                repositorySourceCode,
                referenceDataRepository.getReferenceData(locale.toLanguageTag()).countries().getFirst().code(),
                null,
                null,

                referenceDataRepository.getReferenceData(locale.toLanguageTag()).countries(),

                createAddEditUriComponents()
        );

        return goAddEditForm(locale, data);
    }

    /**
     * This endpoint is the entry point to the editing flow for an existing Repository Source Mirror.
     */
    @RequestMapping(
            value = MultipageConstants.SEGMENT_REPOSITORY_SOURCE_MIRROR + "/{repositorySourceMirrorCode}/" + SEGMENT_EDIT,
            method = RequestMethod.GET,
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ModelAndView editEntry(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable String repositorySourceMirrorCode
    ) {
        RepositorySourceMirrorAddEditSessionData sessionData = new RepositorySourceMirrorAddEditSessionData(
                UUID.randomUUID().toString(),
                null,
                repositorySourceMirrorCode);

        httpServletRequest.getSession(true).setAttribute(
                MultipageDomain.REPOSITORY_SOURCE_MIRROR_ADD_EDIT.getAttributeName(),
                sessionData);

        GetRepositorySourceMirrorResult repositorySourceMirrorResult = repositoryApiService.getRepositorySourceMirror(
                new GetRepositorySourceMirrorRequestEnvelope().code(repositorySourceMirrorCode));

        RepositorySourceMirrorAddEditData data = new RepositorySourceMirrorAddEditData(
                ServletUriComponentsBuilder.fromRequest(httpServletRequest).build(),
                navigationService.deriveMenuGroups(httpServletRequest),
                navigationService.getUserAndNavigation(httpServletRequest),

                sessionData.code(),
                repositorySourceMirrorCode,
                repositorySourceMirrorResult.getRepositorySourceCode(),
                repositorySourceMirrorResult.getCountryCode(),
                repositorySourceMirrorResult.getBaseUrl(),
                repositorySourceMirrorResult.getDescription(),

                referenceDataRepository.getReferenceData(locale.toLanguageTag()).countries(),

                createAddEditUriComponents()
        );

        return goAddEditForm(locale, data);
    }

    /**
     * This endpoint will be hit when the user opts to save the data from the form.
     */

    @RequestMapping(
            value = MultipageConstants.SEGMENT_REPOSITORY_SOURCE_MIRROR_ADD_EDIT,
            method = RequestMethod.POST,
            produces = MediaType.TEXT_HTML_VALUE,
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    public ModelAndView addEdit(
            HttpServletRequest httpServletRequest,
            Locale locale,
            RepositorySourceMirrorAddEditForm form
    ) {
        HttpSession session = httpServletRequest.getSession(false);

        if (null == session) {
            throw new IllegalStateException("missing session");
        }

        RepositorySourceMirrorAddEditSessionData sessionData
                = (RepositorySourceMirrorAddEditSessionData) session.getAttribute(
                        MultipageDomain.REPOSITORY_SOURCE_MIRROR_ADD_EDIT.getAttributeName());

        if (!Objects.equals(sessionData.code(), form.getSessionDataCode())) {
            throw new IllegalStateException("mismatch between session and form");
        }

        String repositorySourceMirrorCode;

        if (StringUtils.isNotBlank(sessionData.editRepositorySourceMirrorCode())) {
            // edit
            UpdateRepositorySourceMirrorRequestEnvelope updateRequest = new UpdateRepositorySourceMirrorRequestEnvelope()
                    .code(sessionData.editRepositorySourceMirrorCode())
                    .description(form.getDescription())
                    .baseUrl(form.getBaseUrl())
                    .countryCode(form.getCountryCode())
                    .filter(List.of(
                            UpdateRepositorySourceMirrorFilter.DESCRIPTION,
                            UpdateRepositorySourceMirrorFilter.BASE_URL,
                            UpdateRepositorySourceMirrorFilter.COUNTRY
                    ));
            repositoryApiService.updateRepositorySourceMirror(updateRequest);
            LOGGER.info("update the repository source mirror [{}]", sessionData.editRepositorySourceMirrorCode());
            repositorySourceMirrorCode = sessionData.editRepositorySourceMirrorCode();
        } else {
            // add
            CreateRepositorySourceMirrorRequestEnvelope createRequest = new CreateRepositorySourceMirrorRequestEnvelope()
                    .description(form.getDescription())
                    .countryCode(form.getCountryCode())
                    .baseUrl(form.getBaseUrl())
                    .repositorySourceCode(sessionData.repositorySourceCode());
            CreateRepositorySourceMirrorResult result = repositoryApiService.createRepositorySourceMirror(createRequest);
            LOGGER.info("created the repository source mirror [{}] on repository source [{}]",
                    result.getCode(), sessionData.repositorySourceCode());
            repositorySourceMirrorCode = result.getCode();
        }

        return new ModelAndView(
                "redirect:%s".formatted(navigationService.repositorySourceMirrorViewUri(httpServletRequest, repositorySourceMirrorCode).toUriString())
        );
    }

    /**
     * This method renders a form that the user can use to add or edit a RepositorySourceMirror.
     */
    private ModelAndView goAddEditForm(
            Locale locale,
            RepositorySourceMirrorAddEditData data
    ) {
        return new ModelAndView(
                "multipage/repository-source-mirror-add-edit",
                Map.of(
                        MultipageConstants.KEY_DATA, data,
                        MultipageConstants.KEY_INTERNATIONALIZATION_SUPPLIER, internationalizationSupplierFactory.create(locale),
                        MultipageConstants.KEY_WEB_RESOURCE_PATH_PREFIXES, multipageWebResourceService.getPathPrefixes()
                )
        );
    }

    private UriComponents createAddEditUriComponents() {
        return UriComponentsBuilder.newInstance()
                .pathSegment(
                        MultipageConstants.SEGMENT_MULTIPAGE,
                        MultipageConstants.SEGMENT_REPOSITORY_SOURCE_MIRROR_ADD_EDIT
                )
                .build();
    }

    /**
     * @param sessionDataCode This is a unique code that should correlate to the state in the session.
     */

    public record RepositorySourceMirrorAddEditData(
            @NotNull UriComponents uriComponents,
            @NotNull List<MenuGroup> menuGroups,
            @NotNull UserAndNavigation userAndNavigation,

            String sessionDataCode,
            String editRepositorySourceMirrorCode,
            String repositorySourceCode,
            String countryCode,
            String baseUrl,
            String description,

            List<Country> countries,

            @NotNull UriComponents addEditActionUriComponents
    ) {
    }

    /**
     * @param code is a short string that is able to correlate the session data with requests.
     */

    public record RepositorySourceMirrorAddEditSessionData(
            String code,
            String repositorySourceCode,
            String editRepositorySourceMirrorCode
    ) implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        static RepositorySourceMirrorAddEditSessionData edit(String repositorySourceMirrorCode) {
            return new RepositorySourceMirrorAddEditSessionData(
                    UUID.randomUUID().toString(), null, repositorySourceMirrorCode);
        }

        static RepositorySourceMirrorAddEditSessionData add(String repositorySourceCode) {
            return new RepositorySourceMirrorAddEditSessionData(
                    UUID.randomUUID().toString(), repositorySourceCode, null);
        }

    }

    /**
     * <p>The data that is coming back from the form.</p>
     */
    public static class RepositorySourceMirrorAddEditForm {

        /**
         * <p>This is a unique code that should correlate to the state in the session.</p>
         */
        @NotNull
        private String sessionDataCode;

        @NotNull
        private String code;

        private String countryCode;
        private String baseUrl;
        private String description;

        public String getSessionDataCode() {
            return sessionDataCode;
        }

        public void setSessionDataCode(String sessionDataCode) {
            this.sessionDataCode = sessionDataCode;
        }

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getCountryCode() {
            return countryCode;
        }

        public void setCountryCode(String countryCode) {
            this.countryCode = countryCode;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

    }

}
