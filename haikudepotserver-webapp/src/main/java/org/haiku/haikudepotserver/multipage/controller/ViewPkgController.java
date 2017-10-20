/*
 * Copyright 2014-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.multipage.controller;

import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.haiku.haikudepotserver.support.web.NaturalLanguageWebHelper;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.haiku.haikudepotserver.multipage.MultipageObjectNotFoundException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * <p>'Page' for showing a version of a package.</p>
 */

@Controller
@RequestMapping(MultipageConstants.PATH_MULTIPAGE + "/pkg")
public class ViewPkgController {

    @Resource
    private ServerRuntime serverRuntime;

    private String hyphenToNull(String part) {
        if(null!=part && !part.equals("-")) {
            return part;
        }

        return null;
    }

    private boolean hasSource(ObjectContext context, PkgVersion pkgVersion) {
        Optional<Pkg> sourcePkgOptional = Pkg.tryGetByName(context, pkgVersion.getPkg().getName() + "_source");
        return sourcePkgOptional.isPresent() &&
                PkgVersion.getForPkg(
                context,
                sourcePkgOptional.get(),
                pkgVersion.getRepositorySource().getRepository(),
                Architecture.getByCode(context, Architecture.CODE_SOURCE).get(),
                pkgVersion.toVersionCoordinates()).isPresent();
    }

    // TODO; Legacy - perhaps remove by late 2015?
    @RequestMapping(value = "{name}/{major}/{minor}/{micro}/{preRelease}/{revision}/{architectureCode}", method = RequestMethod.GET)
    public ModelAndView viewPkg_legacyNoRepository(
            HttpServletRequest httpServletRequest,
            @PathVariable(value="name") String pkgName,
            @PathVariable(value="major") String major,
            @PathVariable(value="minor") String minor,
            @PathVariable(value="micro") String micro,
            @PathVariable(value="preRelease") String preRelease,
            @PathVariable(value="revision") String revisionStr,
            @PathVariable(value="architectureCode") String architectureCode) throws MultipageObjectNotFoundException {
          return viewPkg(
                  httpServletRequest,
                  Repository.CODE_DEFAULT,
                  pkgName, major, minor, micro, preRelease, revisionStr, architectureCode);
    }

    @RequestMapping(value = "{name}/{repositoryCode}/{major}/{minor}/{micro}/{preRelease}/{revision}/{architectureCode}", method = RequestMethod.GET)
    public ModelAndView viewPkg(
            HttpServletRequest httpServletRequest,
            @PathVariable(value="repositoryCode") String repositoryCode,
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

        if(!pkgOptional.isPresent()) {
            throw new MultipageObjectNotFoundException(Pkg.class.getSimpleName(), pkgName); // 404
        }

        Architecture architecture = Architecture.getByCode(context, architectureCode).orElseThrow(() ->
                new MultipageObjectNotFoundException(Architecture.class.getSimpleName(), architectureCode));

        Repository repository = Repository.getByCode(context, repositoryCode).orElseThrow(() ->
                new MultipageObjectNotFoundException(Repository.class.getSimpleName(), repositoryCode));

        VersionCoordinates coordinates = new VersionCoordinates(
                Strings.emptyToNull(major),
                Strings.emptyToNull(minor),
                Strings.emptyToNull(micro),
                Strings.emptyToNull(preRelease),
                revision);

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                context, pkgOptional.get(), repository, architecture, coordinates);

        if(!pkgVersionOptional.isPresent() || !pkgVersionOptional.get().getActive()) {
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

        data.setPkgVersion(pkgVersionOptional.get());
        data.setCurrentNaturalLanguage(NaturalLanguageWebHelper.deriveNaturalLanguage(context, httpServletRequest));
        data.setHomeUrl(homeUrl);
        data.setIsSourceAvailable(hasSource(context, pkgVersionOptional.get()));

        ModelAndView result = new ModelAndView("multipage/viewPkgVersion");
        result.addObject("data", data);

        return result;

    }


    /**
     * <p>This is the data model for the page to be rendered from.</p>
     */

    public static class ViewPkgVersionData {

        private PkgVersion pkgVersion;

        private NaturalLanguage currentNaturalLanguage;

        private String homeUrl;

        private Boolean isSourceAvailable;

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