/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.RenderedPkgIconRepository;
import org.haiku.haikudepotserver.pkg.model.PkgIconExportArchiveJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Optional;

/**
 * <p>This controller vends the package icon.  This may be provided by data stored in the database, or it may be
 * deferring to the default icon.  This controller is also able to take an HTTP PUT request that is able to
 * update a packages icon.  This is not done using JSON-RPC because the binary nature of the data makes transport
 * of the data in JSON impractical.</p>
 */

@Controller
public class PkgIconController extends AbstractController {

    protected static final Logger LOGGER = LoggerFactory.getLogger(PkgIconController.class);

    public final static String SEGMENT_PKGICON = "__pkgicon";

    private final static String SEGMENT_GENERICPKGICON = "__genericpkgicon.png";

    private final static String SEGMENT_ALL_TAR_BALL = "all.tar.gz";

    private final static String KEY_PKGNAME = "pkgname";
    private final static String KEY_FORMAT = "format";
    public final static String KEY_SIZE = "s";
    public final static String KEY_FALLBACK = "f";

    private final ServerRuntime serverRuntime;
    private final PkgIconService pkgIconService;
    private final JobService jobService;
    private final RenderedPkgIconRepository renderedPkgIconRepository;
    private final long startupMillis;

    public PkgIconController(
            ServerRuntime serverRuntime,
            PkgIconService pkgIconService,
            JobService jobService,
            RenderedPkgIconRepository renderedPkgIconRepository) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgIconService = Preconditions.checkNotNull(pkgIconService);
        this.jobService = Preconditions.checkNotNull(jobService);
        this.renderedPkgIconRepository = Preconditions.checkNotNull(renderedPkgIconRepository);
        startupMillis = System.currentTimeMillis();
    }

    /**
     * <p>This method will provide a tar-ball of all of the icons.  This can be used by the desktop application.  It
     * honours the typical <code>If-Modified-Since</code> header and will return the "Not Modified" (304) response
     * if the data that is held by the client is still valid and no new data need to be pulled down.  Otherwise it
     * will return the data to the client; possibly via a re-direct.</p>
     */

    @RequestMapping(value = "/" + SEGMENT_PKGICON + "/" + SEGMENT_ALL_TAR_BALL, method = RequestMethod.GET)
    public void getAllAsTarBall(
            HttpServletResponse response,
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSinceHeader)
            throws IOException {
        JobController.handleRedirectToJobData(
                response,
                jobService,
                ifModifiedSinceHeader,
                pkgIconService.getLastPkgIconModifyTimestampSecondAccuracy(serverRuntime.newContext()),
                new PkgIconExportArchiveJobSpecification());
    }

    @RequestMapping(value = "/" + SEGMENT_GENERICPKGICON, method = RequestMethod.HEAD)
    public void handleGenericHead(
            HttpServletResponse response,
            @RequestParam(value = KEY_SIZE, required = false) Integer size)
            throws IOException {
        handleGenericHeadOrGet(
                RequestMethod.HEAD,
                response,
                size,
                false);
    }

    @RequestMapping(value = "/" + SEGMENT_GENERICPKGICON, method = RequestMethod.GET)
    public void handleGenericGet(
            HttpServletResponse response,
            @RequestParam(value = KEY_SIZE, required = false) Integer size)
            throws IOException {
        handleGenericHeadOrGet(
                RequestMethod.GET,
                response,
                size,
                false);
    }

    @RequestMapping(
            value = "/" + SEGMENT_PKGICON + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}",
            method = RequestMethod.HEAD)
    public void handleHeadPkgIcon(
            HttpServletResponse response,
            @RequestParam(value = KEY_SIZE, required = false) Integer size,
            @PathVariable(value = KEY_FORMAT) String format,
            @PathVariable(value = KEY_PKGNAME) String pkgName,
            @RequestParam(value = KEY_FALLBACK, required = false) Boolean fallback)
            throws IOException {
        handleHeadOrGetPkgIcon(
                RequestMethod.HEAD,
                response,
                size,
                format,
                pkgName,
                fallback);
    }

    @RequestMapping(
            value = "/" + SEGMENT_PKGICON + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}",
            method = RequestMethod.GET)
    public void handleGetPkgIcon(
            HttpServletResponse response,
            @RequestParam(value = KEY_SIZE, required = false) Integer size,
            @PathVariable(value = KEY_FORMAT) String format,
            @PathVariable(value = KEY_PKGNAME) String pkgName,
            @RequestParam(value = KEY_FALLBACK, required = false) Boolean fallback)
            throws IOException {
        handleHeadOrGetPkgIcon(
                RequestMethod.GET,
                response,
                size,
                format,
                pkgName,
                fallback);
    }

    private int normalizeSize(Integer size) {
        Preconditions.checkArgument(null!=size, "the size must be provided");

        if(size < RenderedPkgIconRepository.SIZE_MIN) {
            return RenderedPkgIconRepository.SIZE_MIN;
        }

        if(size > RenderedPkgIconRepository.SIZE_MAX) {
            return RenderedPkgIconRepository.SIZE_MAX;
        }

        return size;
    }

    /**
     * @param isAsFallback is true if the request was originally for a package, but fell back to this generic.
     */

    private void handleGenericHeadOrGet(
            RequestMethod requestMethod,
            HttpServletResponse response,
            Integer size,
            boolean isAsFallback)
            throws IOException {

        if (null==size) {
            size = 64; // largest natural size
        }

        size = normalizeSize(size);
        byte[] data = renderedPkgIconRepository.renderGeneric(size);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(data.length));
        response.setContentType(MediaType.PNG.toString());

        if (isAsFallback) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            response.setHeader(HttpHeaders.PRAGMA, "no-cache");
            response.setHeader(HttpHeaders.EXPIRES, "0");
        }
        else {
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, startupMillis);
        }

        if(requestMethod == RequestMethod.GET) {
            response.getOutputStream().write(data);
        }
    }

    private void handleHeadOrGetPkgIcon(
            RequestMethod requestMethod,
            HttpServletResponse response,
            Integer size,
            String format,
            String pkgName,
            Boolean fallback)
            throws IOException {

        if (null == format) {
            throw new MissingOrBadFormat();
        }

        if (Strings.isNullOrEmpty(pkgName) || !Pkg.PATTERN_NAME.matcher(pkgName).matches()) {
            throw new MissingPkgName();
        }

        ObjectContext context = serverRuntime.newContext();
        Optional<Pkg> pkg = Pkg.tryGetByName(context, pkgName); // cached

        if (pkg.isEmpty()) {
            LOGGER.debug("request for icon for package '{}', but no such package was able to be found", pkgName);
            throw new PkgNotFound();
        }

        PkgSupplement pkgSupplement = pkg.get().getPkgSupplement();

        switch (format) {
            case org.haiku.haikudepotserver.dataobjects.MediaType.EXTENSION_HAIKUVECTORICONFILE -> {
                Optional<PkgIcon> hvifPkgIcon = pkg.get().getPkgSupplement().tryGetPkgIcon(
                        org.haiku.haikudepotserver.dataobjects.MediaType.getByExtension(context, format),
                        null);
                if (hvifPkgIcon.isPresent()) {
                    byte[] data = hvifPkgIcon.get().getPkgIconImage().getData();
                    response.setContentType(org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE);
                    outputToResponse(response, pkgSupplement, data, requestMethod == RequestMethod.GET);
                } else {
                    throw new PkgIconNotFound();
                }
            }
            case org.haiku.haikudepotserver.dataobjects.MediaType.EXTENSION_PNG -> {
                if (null == size) {
                    throw new IllegalArgumentException("the size must be provided when requesting a PNG");
                }
                size = normalizeSize(size);
                Optional<byte[]> pngImageData = renderedPkgIconRepository.render(
                        size, context, pkg.get().getPkgSupplement());
                if (pngImageData.isEmpty()) {
                    if ((null == fallback) || !fallback) {
                        throw new PkgIconNotFound();
                    }

                    handleGenericHeadOrGet(requestMethod, response, size, true);
                } else {
                    byte[] data = pngImageData.get();
                    response.setContentType(MediaType.PNG.toString());
                    outputToResponse(response, pkgSupplement, data, requestMethod == RequestMethod.GET);
                }
            }
            default -> throw new IllegalStateException("unexpected format; " + format);
        }

    }

    private void outputToResponse(
            HttpServletResponse response,
            PkgSupplement pkgSupplement,
            byte[] data,
            boolean streamData) throws IOException {
        response.setHeader(HttpHeaders.CONTENT_LENGTH,Integer.toString(data.length));
        response.setDateHeader(HttpHeaders.LAST_MODIFIED, pkgSupplement.getIconModifyTimestamp().getTime());

        if (streamData) {
            OutputStream outputStream = response.getOutputStream();
            outputStream.write(data);
            outputStream.flush();
        }
    }


    // these are the various errors that can arise in supplying or providing a package icon.

    @ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="the package name must be supplied")
    private static class MissingPkgName extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason="the format must be supplied and must (presently) be 'png'")
    private static class MissingOrBadFormat extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package was unable to found")
    private static class PkgNotFound extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package icon was unable to found")
    private static class PkgIconNotFound extends RuntimeException {}

}
