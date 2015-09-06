/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Optional;

/**
 * <p>This controller allows the user to download a package.</p>
 */

@Controller
public class PkgDownloadController {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgDownloadController.class);

    public final static String SEGMENT_PKGICON = "__pkgdownload";

    public final static String KEY_PKGNAME = "pkgName";
    public final static String KEY_REPOSITORYCODE = "repositoryCode";
    public final static String KEY_MAJOR = "major";
    public final static String KEY_MINOR = "minor";
    public final static String KEY_MICRO = "micro";
    public final static String KEY_PRERELEASE = "preRelease";
    public final static String KEY_REVISION = "revision";
    public final static String KEY_ARCHITECTURECODE = "architectureCode";

    @Resource
    private ServerRuntime serverRuntime;

    private String hyphenToNull(String part) {
        if(null!=part && !part.equals("-")) {
            return part;
        }

        return null;
    }

    @RequestMapping(
            value = "/" + SEGMENT_PKGICON +
                    "/{" + KEY_PKGNAME + "}/{" + KEY_REPOSITORYCODE + "}/{" + KEY_MAJOR + "}/{" + KEY_MINOR + "}/{" +
                    KEY_MICRO + "}/{" + KEY_PRERELEASE + "}/{" + KEY_REVISION + "}/{" + KEY_ARCHITECTURECODE + "}/package.hpkg",
            method = RequestMethod.GET)
    public ResponseEntity download(
            HttpServletResponse response,
            @PathVariable(value = KEY_PKGNAME) String pkgName,
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode,
            @PathVariable(value = KEY_MAJOR) String major,
            @PathVariable(value = KEY_MINOR) String minor,
            @PathVariable(value = KEY_MICRO) String micro,
            @PathVariable(value = KEY_PRERELEASE) String prerelease,
            @PathVariable(value = KEY_REVISION) String revisionStr,
            @PathVariable(value = KEY_ARCHITECTURECODE) String architectureCode) throws IOException {

        Preconditions.checkArgument(null!=response, "the response is required");

        ObjectContext context = serverRuntime.getContext();

        Optional<Pkg> pkgOptional = Pkg.getByName(context, pkgName);

        if(!pkgOptional.isPresent()) {
            LOGGER.info("unable to find the package; {}", pkgName);
            return ResponseEntity.notFound().build();
        }

        Optional<Repository> repositoryOptional = Repository.getByCode(context, repositoryCode);

        if(!repositoryOptional.isPresent()) {
            LOGGER.info("unable to find the repository; {}", repositoryCode);
            return ResponseEntity.notFound().build();
        }

        Optional<Architecture> architectureOptional = Architecture.getByCode(context, architectureCode);

        if(!architectureOptional.isPresent()) {
            LOGGER.info("unable to find the architecture; {}", architectureCode);
            return ResponseEntity.notFound().build();
        }

        revisionStr = hyphenToNull(revisionStr);

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                hyphenToNull(major),
                hyphenToNull(minor),
                hyphenToNull(micro),
                hyphenToNull(prerelease),
                null==revisionStr ? null : Integer.parseInt(revisionStr));

        Optional<PkgVersion> pkgVersionOptional = PkgVersion.getForPkg(
                context,
                pkgOptional.get(),
                repositoryOptional.get(),
                architectureOptional.get(),
                versionCoordinates);

        if(!pkgVersionOptional.isPresent()) {
            LOGGER.info("unable to find the pkg version; {}, {}", pkgName, versionCoordinates);
            return ResponseEntity.notFound().build();
        }

        URL url = pkgVersionOptional.get().getHpkgURL();
        InputStream inputStream = url.openStream();
        org.springframework.http.HttpHeaders httpHeaders = new org.springframework.http.HttpHeaders();
        httpHeaders.setContentType(org.springframework.http.MediaType.APPLICATION_OCTET_STREAM);
        httpHeaders.set(
                HttpHeaders.CONTENT_DISPOSITION,
                String.format(
                        "attachment; filename=\"%s\"",
                        pkgVersionOptional.get().getHpkgFilename())
        );

        LOGGER.info("downloaded package version; {}", pkgVersionOptional.get());

        return new ResponseEntity<>(inputStream, httpHeaders, HttpStatus.OK);
    }

}
