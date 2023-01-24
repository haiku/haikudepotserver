/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.model.Pagination;
import org.haiku.haikudepotserver.pkg.FixedPkgLocalizationLookupServiceImpl;
import org.haiku.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.support.AbstractSearchSpecification;
import org.haiku.haikudepotserver.support.web.NaturalLanguageWebHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * <p>Renders the home page of the multi-page (simple) view of the application.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE)
public class HomeController {

    /**
     * <p>This defines the type of display of packages that are shown.</p>
     */

    public enum ViewCriteriaType {
        FEATURED,
        ALL,
        CATEGORIES,
        MOSTRECENT,
        MOSTVIEWED;

        public String getTitleKey() {
            return "home.viewCriteriaType." + name().toLowerCase();
        }

    }

    // these should correspond to the single-page keys for the home page.
    private final static String KEY_OFFSET = "o";
    private final static String KEY_REPOSITORIESCODES = "repos";
    private final static String KEY_ARCHITECTURECODE = "arch";
    private final static String KEY_PKGCATEGORYCODE = "pkgcat";
    private final static String KEY_SEARCHEXPRESSION = "srchexpr";
    private final static String KEY_VIEWCRITERIATYPECODE = "viewcrttyp";

    private final static int PAGESIZE = 15;

    private final ServerRuntime serverRuntime;
    private final PkgService pkgService;
    private final String defaultArchitectureCode;

    public HomeController(
            ServerRuntime serverRuntime,
            PkgService pkgService,
            @Value("${architecture.default.code}") String defaultArchitectureCode) {
        this.serverRuntime = serverRuntime;
        this.pkgService = pkgService;
        this.defaultArchitectureCode = defaultArchitectureCode;
    }

    /**
     * <p>This is the entry point for the home page.  It will look at the parameters supplied and will
     * establish what should be displayed.</p>
     */

    @RequestMapping(method = RequestMethod.GET)
    public ModelAndView home(
            HttpServletRequest httpServletRequest,
            @RequestParam(value = KEY_OFFSET, defaultValue = "0") Integer offset,
            @RequestParam(value = KEY_REPOSITORIESCODES, required = false) String repositoryCodes,
            @RequestParam(value = KEY_ARCHITECTURECODE, required = false) String architectureCode,
            @RequestParam(value = KEY_PKGCATEGORYCODE, required = false) String pkgCategoryCode,
            @RequestParam(value = KEY_SEARCHEXPRESSION, required = false) String searchExpression,
            @RequestParam(value = KEY_VIEWCRITERIATYPECODE, required = false) ViewCriteriaType viewCriteriaType) {

        ObjectContext context = serverRuntime.newContext();

        if (Strings.isNullOrEmpty(architectureCode)) {
            architectureCode = defaultArchitectureCode;
        }

        if (null == repositoryCodes) {
            repositoryCodes = Repository.CODE_DEFAULT;
        }

        // ------------------------------
        // FETCH THE DATA

        PkgSearchSpecification searchSpecification = new PkgSearchSpecification();

        searchSpecification.setOffset(offset);
        searchSpecification.setLimit(PAGESIZE);
        searchSpecification.setExpression(searchExpression);
        searchSpecification.setExpressionType(AbstractSearchSpecification.ExpressionType.CONTAINS);

        Repository repository = StringUtils.isBlank(repositoryCodes) ? null : Repository.getByCode(context, repositoryCodes);
        searchSpecification.setRepositories(null == repository ? Repository.getAllActive(context) : Collections.singletonList(repository));

        Architecture architecture = Architecture.getByCode(context, architectureCode);
        searchSpecification.setArchitecture(architecture);

        Optional<PkgCategory> pkgCategoryOptional = Optional.empty();

        if (null != pkgCategoryCode) {
            pkgCategoryOptional = PkgCategory.tryGetByCode(context, pkgCategoryCode);
        }

        NaturalLanguage naturalLanguage = NaturalLanguageWebHelper.deriveNaturalLanguage(context, httpServletRequest);
        searchSpecification.setNaturalLanguage(naturalLanguage);

        switch (null == viewCriteriaType ? ViewCriteriaType.FEATURED : viewCriteriaType) {
            case FEATURED -> searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.PROMINENCE);
            case CATEGORIES -> {
                searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.NAME);
                searchSpecification.setPkgCategory(pkgCategoryOptional.orElseThrow(() ->
                        new IllegalStateException(
                                "the pkg category code was unable to be found; " + pkgCategoryCode)));
            }
            case ALL -> searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.NAME);
            case MOSTVIEWED ->
                    searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.VERSIONVIEWCOUNTER);
            case MOSTRECENT ->
                    searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.VERSIONCREATETIMESTAMP);
            default -> throw new IllegalStateException("unhandled view criteria type");
        }

        Long totalPkgVersions = pkgService.total(context, searchSpecification);

        if(searchSpecification.getOffset() > totalPkgVersions) {
            searchSpecification.setOffset(totalPkgVersions.intValue());
        }

        List<PkgVersion> pkgVersions = pkgService.search(context, searchSpecification);

        // ------------------------------
        // GENERATE OUTPUT

        HomeData data = new HomeData();

        data.setNaturalLanguage(naturalLanguage);

        final Set<String> excludedArchitectureCode = ImmutableSet.of(
                Architecture.CODE_ANY,
                Architecture.CODE_SOURCE
        );

        data.setAllArchitectures(
                Architecture.getAll(context)
                        .stream()
                        .filter(a -> !excludedArchitectureCode.contains(a.getCode()))
                        .collect(Collectors.toList()));

        data.setArchitecture(architecture);
        data.setRepository(repository);

        data.setAllRepositories(Repository.getAllActive(context));
        data.setAllPkgCategories(PkgCategory.getAll(context));
        data.setPkgCategory(pkgCategoryOptional.orElseGet(() -> PkgCategory.getAll(context).get(0)));

        data.setAllViewCriteriaTypes(ImmutableList.copyOf(ViewCriteriaType.values()));
        data.setViewCriteriaType(viewCriteriaType);

        data.setSearchExpression(searchExpression);
        data.setPkgVersions(pkgVersions);

        if (0 != totalPkgVersions.intValue()) {
            data.setPagination(new Pagination(totalPkgVersions.intValue(), offset, PAGESIZE));
        }

        httpServletRequest.setAttribute(
                MultipageConstants.KEY_PKGLOCALIZATIONLOOKUPSERVICE,
                new FixedPkgLocalizationLookupServiceImpl(context, pkgVersions, naturalLanguage));

        httpServletRequest.setAttribute(MultipageConstants.KEY_SERVERRUNTIME, serverRuntime);

        ModelAndView result = new ModelAndView("multipage/home");
        result.addObject("data", data);

        return result;
    }

    /**
     * <p>This is the data model for the page to be rendered from.</p>
     */

    @SuppressWarnings("WeakerAccess") // required for Thymeleaf rendering.
    public static class HomeData {

        private NaturalLanguage naturalLanguage;

        private List<PkgVersion> pkgVersions;

        private List<Repository> allRepositories;

        private List<Architecture> allArchitectures;

        private List<PkgCategory> allPkgCategories;

        private List<ViewCriteriaType> allViewCriteriaTypes;

        private Architecture architecture;

        private Repository repository;

        private PkgCategory pkgCategory;

        private String searchExpression;

        private ViewCriteriaType viewCriteriaType;

        private Pagination pagination;

        public Repository getRepository() {
            return repository;
        }

        public void setRepository(Repository repository) {
            this.repository = repository;
        }

        public NaturalLanguage getNaturalLanguage() {
            return naturalLanguage;
        }

        public void setNaturalLanguage(NaturalLanguage naturalLanguage) {
            this.naturalLanguage = naturalLanguage;
        }

        public List<PkgVersion> getPkgVersions() {
            return pkgVersions;
        }

        public void setPkgVersions(List<PkgVersion> pkgVersions) {
            this.pkgVersions = pkgVersions;
        }

        public List<Repository> getAllRepositories() {
            return allRepositories;
        }

        public void setAllRepositories(List<Repository> allRepositories) {
            this.allRepositories = allRepositories;
        }

        public List<Architecture> getAllArchitectures() {
            return allArchitectures;
        }

        public void setAllArchitectures(List<Architecture> allArchitectures) {
            this.allArchitectures = allArchitectures;
        }

        public Architecture getArchitecture() {
            return architecture;
        }

        public void setArchitecture(Architecture architecture) {
            this.architecture = architecture;
        }

        public String getSearchExpression() {
            return searchExpression;
        }

        public void setSearchExpression(String searchExpression) {
            this.searchExpression = searchExpression;
        }

        public List<PkgCategory> getAllPkgCategories() {
            return allPkgCategories;
        }

        public void setAllPkgCategories(List<PkgCategory> allPkgCategories) {
            this.allPkgCategories = allPkgCategories;
        }

        public List<ViewCriteriaType> getAllViewCriteriaTypes() {
            return allViewCriteriaTypes;
        }

        public void setAllViewCriteriaTypes(List<ViewCriteriaType> allViewCriteriaTypes) {
            this.allViewCriteriaTypes = allViewCriteriaTypes;
        }

        public PkgCategory getPkgCategory() {
            return pkgCategory;
        }

        public void setPkgCategory(PkgCategory pkgCategory) {
            this.pkgCategory = pkgCategory;
        }

        public ViewCriteriaType getViewCriteriaType() {
            return viewCriteriaType;
        }

        public void setViewCriteriaType(ViewCriteriaType viewCriteriaType) {
            this.viewCriteriaType = viewCriteriaType;
        }

        public Pagination getPagination() {
            return pagination;
        }

        public void setPagination(Pagination pagination) {
            this.pagination = pagination;
        }
    }

}
