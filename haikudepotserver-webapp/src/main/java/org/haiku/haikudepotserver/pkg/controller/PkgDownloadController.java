/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.pkg.PkgServiceImpl;
import org.haiku.haikudepotserver.support.ExposureType;
import org.haiku.haikudepotserver.support.VersionCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.Optional;

/**
 * <p>This controller allows the user to download a package.</p>
 */

@Controller
public class PkgDownloadController {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgDownloadController.class);

    // TODO; should be injected into the orchestration service.
    private final static String SEGMENT_PKGDOWNLOAD = PkgServiceImpl.URL_SEGMENT_PKGDOWNLOAD;

    private final static String KEY_PKGNAME = "pkgName";
    private final static String KEY_REPOSITORYCODE = "repositoryCode";
    private final static String KEY_REPOSITORYSOURCECODE = "repositorySourceCode";
    private final static String KEY_MAJOR = "major";
    private final static String KEY_MINOR = "minor";
    private final static String KEY_MICRO = "micro";
    private final static String KEY_PRERELEASE = "preRelease";
    private final static String KEY_REVISION = "revision";
    private final static String KEY_ARCHITECTURECODE = "architectureCode";

    private final static String PATH = "/{" + KEY_PKGNAME + "}/{" + KEY_REPOSITORYCODE + "}/{" + KEY_REPOSITORYSOURCECODE + "}/{" + KEY_MAJOR + "}/{" + KEY_MINOR + "}/{" +
            KEY_MICRO + "}/{" + KEY_PRERELEASE + "}/{" + KEY_REVISION + "}/{" + KEY_ARCHITECTURECODE + "}/package.hpkg";

    private final ServerRuntime serverRuntime;

    public PkgDownloadController(ServerRuntime serverRuntime) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
    }

    private String hyphenToNull(String part) {
        if (null!=part && !part.equals("-")) {
            return part;
        }

        return null;
    }

    @RequestMapping(
            value = { "/" + SEGMENT_PKGDOWNLOAD + PATH },
            method = RequestMethod.GET)
    public void download(
            HttpServletResponse response,
            @PathVariable(value = KEY_PKGNAME) String pkgName,
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode,
            @PathVariable(value = KEY_REPOSITORYSOURCECODE) String repositorySourceCode,
            @PathVariable(value = KEY_MAJOR) String major,
            @PathVariable(value = KEY_MINOR) String minor,
            @PathVariable(value = KEY_MICRO) String micro,
            @PathVariable(value = KEY_PRERELEASE) String prerelease,
            @PathVariable(value = KEY_REVISION) String revisionStr,
            @PathVariable(value = KEY_ARCHITECTURECODE) String architectureCode)
            throws
            IOException, RequestObjectNotFound {

        Preconditions.checkArgument(null!=response, "the response is required");

        ObjectContext context = serverRuntime.newContext();

        Pkg pkg = Pkg.tryGetByName(context, pkgName).orElseThrow(() -> {
            LOGGER.info("unable to find the package; {}", pkgName);
            return new RequestObjectNotFound();
        });

        RepositorySource repositorySource = RepositorySource.tryGetByCode(context, repositorySourceCode).orElseThrow(() -> {
            LOGGER.info("unable to find the repository source; {}", repositorySourceCode);
            return new RequestObjectNotFound();
        });

        // extra check; probably not required

        if (!repositorySource.getRepository().getCode().equals(repositoryCode)) {
            LOGGER.info("mismatch between the repository source [{}] and repository [{}]",
                    repositorySourceCode, repositoryCode);
            throw new RequestObjectNotFound();
        }

        Architecture architecture = Architecture.tryGetByCode(context, architectureCode).orElseThrow(() -> {
            LOGGER.info("unable to find the architecture; {}", architectureCode);
            return new RequestObjectNotFound();
        });

        revisionStr = hyphenToNull(revisionStr);

        VersionCoordinates versionCoordinates = new VersionCoordinates(
                hyphenToNull(major),
                hyphenToNull(minor),
                hyphenToNull(micro),
                hyphenToNull(prerelease),
                null == revisionStr ? null : Integer.parseInt(revisionStr));

        PkgVersion pkgVersion = PkgVersion.tryGetForPkg(context, pkg, repositorySource, architecture, versionCoordinates)
                .orElseThrow(() -> {
                    LOGGER.info("unable to find the pkg version; {}, {}", pkgName, versionCoordinates);
                    return new RequestObjectNotFound();
                });

        Optional<URL> urlOptional = pkgVersion.tryGetHpkgURL(ExposureType.EXTERNAL_FACING);

        if (urlOptional.isEmpty()) {
            LOGGER.info("unable to allow download of the hpkg data as no url was able to be generated");
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        } else {
            URL url = urlOptional.get();

            // if it is an HTTP URL then it should be possible to redirect the browser to that URL
            // instead of piping it through the application server.

            if (ImmutableSet.of("http", "https").contains(url.getProtocol())) {
                response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                response.setHeader(HttpHeaders.LOCATION, url.toString());
                response.setContentType(MediaType.PLAIN_TEXT_UTF_8.toString());

                PrintWriter writer = response.getWriter();
                writer.print(url);
                writer.flush();
            } else {
                response.setContentType(MediaType.OCTET_STREAM.toString());
                response.setHeader(
                        HttpHeaders.CONTENT_DISPOSITION,
                        String.format(
                                "attachment; filename=\"%s\"",
                                pkgVersion.getHpkgFilename())
                );

                try (InputStream inputStream = url.openStream()) {
                    LOGGER.info("downloaded package version; {} - {}", pkg.getName(), pkgVersion);
                    ByteStreams.copy(inputStream, response.getOutputStream());
                } catch (IOException ioe) {
                    // logged without a stack trace because it happens fairly often that a robot will initiate the download and then drop it.
                    LOGGER.error("unable to relay data to output stream from '{}'; {} -- {}",
                            url, ioe.getClass().getSimpleName(), ioe.getMessage());
                }
            }
        }
    }

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package was unable to found")
    private static class RequestObjectNotFound extends RuntimeException {}

}
