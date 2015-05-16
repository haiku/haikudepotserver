/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.multipage.controller;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.PkgCategory;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.multipage.MultipageConstants;
import org.haikuos.haikudepotserver.support.web.NaturalLanguageWebHelper;
import org.haikuos.haikudepotserver.multipage.model.Pagination;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.support.AbstractSearchSpecification;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.ModelAndView;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
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
    public final static String KEY_OFFSET = "o";
    public final static String KEY_ARCHITECTURECODE = "arch";
    public final static String KEY_PKGCATEGORYCODE = "pkgcat";
    public final static String KEY_SEARCHEXPRESSION = "srchexpr";
    public final static String KEY_VIEWCRITERIATYPECODE = "viewcrttyp";

    public final static int PAGESIZE = 15;

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Value("${architecture.default.code}")
    private String defaultArchitectureCode;

    /**
     * <p>This is the entry point for the home page.  It will look at the parameters supplied and will
     * establish what should be displayed.</p>
     */

    @RequestMapping(method = RequestMethod.GET)
    public ModelAndView home(
            HttpServletRequest httpServletRequest,
            @RequestParam(value=KEY_OFFSET, defaultValue = "0") Integer offset,
            @RequestParam(value=KEY_ARCHITECTURECODE, required=false) String architectureCode,
            @RequestParam(value=KEY_PKGCATEGORYCODE, required=false) String pkgCategoryCode,
            @RequestParam(value=KEY_SEARCHEXPRESSION, required=false) String searchExpression,
            @RequestParam(value=KEY_VIEWCRITERIATYPECODE, required=false) ViewCriteriaType viewCriteriaType) {

        ObjectContext context = serverRuntime.getContext();

        if(Strings.isNullOrEmpty(architectureCode)) {
            architectureCode = defaultArchitectureCode;
        }

        // ------------------------------
        // FETCH THE DATA

        PkgSearchSpecification searchSpecification = new PkgSearchSpecification();

        searchSpecification.setOffset(offset);
        searchSpecification.setLimit(PAGESIZE);
        searchSpecification.setExpression(searchExpression);
        searchSpecification.setExpressionType(AbstractSearchSpecification.ExpressionType.CONTAINS);

        Optional<Architecture> architectureOptional = Architecture.getByCode(context, architectureCode);

        if(!architectureOptional.isPresent()) {
            throw new IllegalStateException("unable to obtain the architecture; " + architectureCode);
        }

        searchSpecification.setArchitectures(
                ImmutableList.of(
                        architectureOptional.get(),
                        Architecture.getByCode(context, Architecture.CODE_ANY).get()
                )
        );

        Optional<PkgCategory> pkgCategoryOptional = Optional.empty();

        if(null!=pkgCategoryCode) {
            pkgCategoryOptional = PkgCategory.getByCode(context, pkgCategoryCode);
        }

        searchSpecification.setNaturalLanguage(NaturalLanguageWebHelper.deriveNaturalLanguage(context, httpServletRequest));

        switch(null==viewCriteriaType ? ViewCriteriaType.FEATURED : viewCriteriaType) {

            case FEATURED:
                searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.PROMINENCE);
                break;

            case CATEGORIES:
                searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.NAME);

                if(!pkgCategoryOptional.isPresent()) {
                    throw new IllegalStateException("the pkg category code was unable to be found; " + pkgCategoryCode);
                }

                searchSpecification.setPkgCategory(pkgCategoryOptional.get());

                break;

            case ALL:
                searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.NAME);
                break;

            case MOSTVIEWED:
                searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.VERSIONVIEWCOUNTER);
                break;

            case MOSTRECENT:
                searchSpecification.setSortOrdering(PkgSearchSpecification.SortOrdering.VERSIONCREATETIMESTAMP);
                break;

            default:
                throw new IllegalStateException("unhandled view criteria type");

        }

        Long totalPkgVersions = pkgOrchestrationService.total(context, searchSpecification);

        if(searchSpecification.getOffset() > totalPkgVersions) {
            searchSpecification.setOffset(totalPkgVersions.intValue());
        }

        List<PkgVersion> pkgVersions = pkgOrchestrationService.search(context, searchSpecification);

        // ------------------------------
        // GENERATE OUTPUT

        HomeData data = new HomeData();

        final Set<String> excludedArchitectureCode = ImmutableSet.of(
                Architecture.CODE_ANY,
                Architecture.CODE_SOURCE
        );

        data.setAllArchitectures(
                Architecture.getAll(context)
                        .stream()
                        .filter(a -> !excludedArchitectureCode.contains(a.getCode()))
                        .collect(Collectors.toList()));

        data.setArchitecture(architectureOptional.get());

        data.setAllPkgCategories(PkgCategory.getAll(context));
        data.setPkgCategory(pkgCategoryOptional.isPresent() ? pkgCategoryOptional.get() : PkgCategory.getAll(context).get(0));

        data.setAllViewCriteriaTypes(ImmutableList.copyOf(ViewCriteriaType.values()));
        data.setViewCriteriaType(viewCriteriaType);

        data.setSearchExpression(searchExpression);
        data.setPkgVersions(pkgVersions);

        if(0!=totalPkgVersions.intValue()) {
            data.setPagination(new Pagination(totalPkgVersions.intValue(), offset, PAGESIZE));
        }

        ModelAndView result = new ModelAndView("multipage/home");
        result.addObject("data", data);

        return result;
    }

    /**
     * <p>This is the data model for the page to be rendered from.</p>
     */

    public static class HomeData {

        private List<PkgVersion> pkgVersions;

        private List<Architecture> allArchitectures;

        private List<PkgCategory> allPkgCategories;

        private List<ViewCriteriaType> allViewCriteriaTypes;

        private Architecture architecture;

        private PkgCategory pkgCategory;

        private String searchExpression;

        private ViewCriteriaType viewCriteriaType;

        private Pagination pagination;

        public List<PkgVersion> getPkgVersions() {
            return pkgVersions;
        }

        public void setPkgVersions(List<PkgVersion> pkgVersions) {
            this.pkgVersions = pkgVersions;
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
