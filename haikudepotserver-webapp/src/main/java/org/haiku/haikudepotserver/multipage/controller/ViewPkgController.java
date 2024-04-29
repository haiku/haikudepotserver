/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageObjectNotFoundException;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Locale;
import java.util.Optional;

/**
 * <p>'Page' for showing a version of a package.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/pkg")
public class ViewPkgController {

    private final ServerRuntime serverRuntime;

    private final PkgLocalizationService pkgLocalizationService;

    public ViewPkgController(
            ServerRuntime serverRuntime,
            PkgLocalizationService pkgLocalizationService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgLocalizationService = Preconditions.checkNotNull(pkgLocalizationService);
    }

    private String hyphenToNull(String part) {
        if (null != part && !part.equals("-")) {
            return part;
        }

        return null;
    }

    private boolean hasSource(ObjectContext context, PkgVersion pkgVersion) {
        Optional<Pkg> sourcePkgOptional = Pkg.tryGetByName(context, pkgVersion.getPkg().getName() + "_source");
        return sourcePkgOptional.isPresent() &&
                PkgVersion.tryGetForPkg(
                context,
                sourcePkgOptional.get(),
                pkgVersion.getRepositorySource(),
                Architecture.getByCode(context, Architecture.CODE_SOURCE),
                pkgVersion.toVersionCoordinates()).isPresent();
    }

    @RequestMapping(value = "{name}/{repositoryCode}/{repositorySourceCode}/{major}/{minor}/{micro}/{preRelease}/{revision}/{architectureCode}", method = RequestMethod.GET)
    public ModelAndView viewPkg(
            HttpServletRequest httpServletRequest,
            Locale locale,
            @PathVariable(value="repositoryCode") String repositoryCode,
            @PathVariable(value="repositorySourceCode") String repositorySourceCode,
            @PathVariable(value="name") String pkgName,
            @PathVariable(value="major") String major,
            @PathVariable(value="minor") String minor,
            @PathVariable(value="micro") String micro,
            @PathVariable(value="preRelease") String preRelease,
            @PathVariable(value="revision") String revisionStr,
            @PathVariable(value="architectureCode") String architectureCode) throws MultipageObjectNotFoundException {

        major = hyphenToNull(major);
        minor = hyphenToNull(minor);
        micro = hyphenToNull(micro);
        preRelease = hyphenToNull(preRelease);
        revisionStr = hyphenToNull(revisionStr);

        Integer revision = null==revisionStr ? null : Integer.parseInt(revisionStr);

        ObjectContext context = serverRuntime.newContext();
        Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, pkgName);

        if(pkgOptional.isEmpty()) {
            throw new MultipageObjectNotFoundException(Pkg.class.getSimpleName(), pkgName); // 404
        }

        Architecture architecture = Architecture.tryGetByCode(context, architectureCode).orElseThrow(() ->
                new MultipageObjectNotFoundException(Architecture.class.getSimpleName(), architectureCode));

        RepositorySource repositorySource = RepositorySource.tryGetByCode(context, repositorySourceCode).orElseThrow(() ->
                new MultipageObjectNotFoundException(Repository.class.getSimpleName(), repositorySourceCode));

        // possibly unnecessary extra check
        if (!repositorySource.getRepository().getCode().equals(repositoryCode)) {
            throw new MultipageObjectNotFoundException(Repository.class.getSimpleName(), repositoryCode);
        }

        VersionCoordinates coordinates = new VersionCoordinates(
                Strings.emptyToNull(major),
                Strings.emptyToNull(minor),
                Strings.emptyToNull(micro),
                Strings.emptyToNull(preRelease),
                revision);

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.tryGetForPkg(
                context, pkgOptional.get(), repositorySource, architecture, coordinates);

        if(pkgVersionOptional.isEmpty() || !pkgVersionOptional.get().getActive()) {
            throw new MultipageObjectNotFoundException(PkgVersion.class.getSimpleName(), pkgName + "...");
        }

        String homeUrl;

        {
            UriComponentsBuilder builder = UriComponentsBuilder.fromPath(MultipageConstants.PATH_MULTIPAGE);
            String naturalLanguageCode = httpServletRequest.getParameter(WebConstants.KEY_NATURALLANGUAGECODE);

            if(!Strings.isNullOrEmpty(naturalLanguageCode)) {
                builder.queryParam(WebConstants.KEY_NATURALLANGUAGECODE, naturalLanguageCode);
            }

            homeUrl = builder.build().toString();
        }

        ViewPkgVersionData data = new ViewPkgVersionData();
        NaturalLanguage naturalLanguage = NaturalLanguage.getByNaturalLanguage(context, NaturalLanguageCoordinates.fromLocale(locale));

        data.setPkgVersion(pkgVersionOptional.get());
        data.setResolvedPkgVersionLocalization(
                pkgLocalizationService.resolvePkgVersionLocalization(
                        context,
                        pkgVersionOptional.get(),
                        null,
                        naturalLanguage
                )
        );
        data.setCurrentNaturalLanguage(naturalLanguage);
        data.setHomeUrl(homeUrl);
        data.setIsSourceAvailable(hasSource(context, pkgVersionOptional.get()));

        ModelAndView result = new ModelAndView("multipage/viewPkgVersion");
        result.addObject("data", data);
        result.addObject("request", httpServletRequest);
        return result;
    }


    /**
     * <p>This is the data model for the page to be rendered from.</p>
     */

    public static class ViewPkgVersionData {

        private PkgVersion pkgVersion;

        private ResolvedPkgVersionLocalization resolvedPkgVersionLocalization;

        private NaturalLanguage currentNaturalLanguage;

        private String homeUrl;

        private Boolean isSourceAvailable;

        public ResolvedPkgVersionLocalization getResolvedPkgVersionLocalization() {
            return resolvedPkgVersionLocalization;
        }

        public void setResolvedPkgVersionLocalization(ResolvedPkgVersionLocalization resolvedPkgVersionLocalization) {
            this.resolvedPkgVersionLocalization = resolvedPkgVersionLocalization;
        }

        public Boolean getIsSourceAvailable() {
            return isSourceAvailable;
        }

        public void setIsSourceAvailable(Boolean isSourceAvailable) {
            this.isSourceAvailable = isSourceAvailable;
        }

        public PkgVersion getPkgVersion() {
            return pkgVersion;
        }

        public void setPkgVersion(PkgVersion pkgVersion) {
            this.pkgVersion = pkgVersion;
        }

        public NaturalLanguage getCurrentNaturalLanguage() {
            return currentNaturalLanguage;
        }

        public void setCurrentNaturalLanguage(NaturalLanguage currentNaturalLanguage) {
            this.currentNaturalLanguage = currentNaturalLanguage;
        }

        public String getHomeUrl() {
            return homeUrl;
        }

        public void setHomeUrl(String homeUrl) {
            this.homeUrl = homeUrl;
        }
    }


}
