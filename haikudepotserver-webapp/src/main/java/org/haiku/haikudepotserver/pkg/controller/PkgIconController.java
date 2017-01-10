/*
 * Copyright 2013-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang.StringUtils;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.RenderedPkgIconRepository;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgIcon;
import org.haiku.haikudepotserver.pkg.model.PkgIconExportArchiveJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.security.AuthenticationFilter;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

/**
 * <p>This controller vends the package icon.  This may be provided by data stored in the database, or it may be
 * deferring to the default icon.  This controller is also able to take an HTTP PUT request that is able to
 * update a packages icon.  This is not done using JSON-RPC because the binary nature of the data makes transport
 * of the data in JSON impractical.</p>
 */

@Controller
public class PkgIconController extends AbstractController {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgIconController.class);

    public final static String SEGMENT_PKGICON = "__pkgicon";

    @Deprecated
    private final static String SEGMENT_PKGICON_LEGACY = "pkgicon";

    public final static String SEGMENT_GENERICPKGICON = "__genericpkgicon.png";

    @Deprecated
    private final static String SEGMENT_GENERICPKGICON_LEGACY = "genericpkgicon.png";

    private final static String SEGMENT_ALL_TAR_BALL = "all.tar.gz";

    public final static String KEY_PKGNAME = "pkgname";
    public final static String KEY_FORMAT = "format";
    public final static String KEY_SIZE = "s";
    public final static String KEY_FALLBACK = "f";

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgIconService pkgIconService;

    @Resource
    private JobService jobService;

    @Resource
    private RenderedPkgIconRepository renderedPkgIconRepository;

    private long startupMillis = System.currentTimeMillis();

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

        ObjectContext context = serverRuntime.getContext();
        Date modifyTimestamp = pkgIconService.getLastPkgIconModifyTimestampSecondAccuracy(context);

        if (!Strings.isNullOrEmpty(ifModifiedSinceHeader)) {
            try {
                Date requestModifyTimestamp = new Date(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSinceHeader)).toEpochMilli());

                if (requestModifyTimestamp.getTime() >= modifyTimestamp.getTime()) {
                    response.setStatus(HttpStatus.NOT_MODIFIED.value());
                    return;
                }
            } catch (DateTimeParseException dtpe) {
                LOGGER.warn("bad [{}] header on request; [{}] -- will ignore",
                        HttpHeaders.IF_MODIFIED_SINCE,
                        StringUtils.abbreviate(ifModifiedSinceHeader, 128));
            }
        }

        PkgIconExportArchiveJobSpecification specification = new PkgIconExportArchiveJobSpecification();
        String jobGuid = jobService.immediate(specification, true);
        JobSnapshot jobSnapshot = jobService.tryGetJob(jobGuid)
                .orElseThrow(() -> new IllegalStateException("unable to obtain the job snapshot having run it immediate prior."));
        Set<String> jobDataGuids = jobSnapshot.getDataGuids();

        if (1 != jobDataGuids.size()) {
            throw new IllegalStateException("found [" + jobDataGuids.size() + "] job data guids related to the job [" + jobGuid + "] - was expecting 1");
        }

        String lastModifiedValue = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(
                modifyTimestamp.toInstant(), ZoneOffset.UTC));

        response.addHeader(HttpHeaders.LAST_MODIFIED, lastModifiedValue);
        response.sendRedirect(UriComponentsBuilder.newInstance()
                .pathSegment(AuthenticationFilter.SEGMENT_SECURED)
                .pathSegment(JobController.SEGMENT_JOBDATA)
                .pathSegment(jobDataGuids.iterator().next())
                .pathSegment(JobController.SEGMENT_DOWNLOAD)
                .toUriString());
    }

    @RequestMapping(value = {
            "/" + SEGMENT_GENERICPKGICON,
            "/" + SEGMENT_GENERICPKGICON_LEGACY // TODO; remove
    }, method = RequestMethod.HEAD)
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

    @RequestMapping(value = {
            "/" + SEGMENT_GENERICPKGICON,
            "/" + SEGMENT_GENERICPKGICON_LEGACY // TODO; remove
    }, method = RequestMethod.GET)
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

    @RequestMapping(value = {
            "/" + SEGMENT_PKGICON + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}",
            "/" + SEGMENT_PKGICON_LEGACY + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}", // TODO; remove
    }, method = RequestMethod.HEAD)
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

    @RequestMapping(value = {
            "/" + SEGMENT_PKGICON + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}",
            "/" + SEGMENT_PKGICON_LEGACY + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}" // TODO; remove
    }, method = RequestMethod.GET)
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

        if(null==size) {
            size = 64; // largest natural size
        }

        size = normalizeSize(size);
        byte[] data = renderedPkgIconRepository.renderGeneric(size);
        response.setHeader(HttpHeaders.CONTENT_LENGTH, Integer.toString(data.length));
        response.setContentType(MediaType.PNG.toString());

        if(isAsFallback) {
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

        if(null==format) {
            throw new MissingOrBadFormat();
        }

        if(Strings.isNullOrEmpty(pkgName) || !Pkg.PATTERN_NAME.matcher(pkgName).matches()) {
            throw new MissingPkgName();
        }

        ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkg = Pkg.getByName(context, pkgName); // cached

        if(!pkg.isPresent()) {
            LOGGER.debug("request for icon for package '{}', but no such package was able to be found", pkgName);
            throw new PkgNotFound();
        }

        switch(format) {

            case org.haiku.haikudepotserver.dataobjects.MediaType.EXTENSION_HAIKUVECTORICONFILE:
                Optional<PkgIcon> hvifPkgIcon = pkg.get().getPkgIcon(
                        org.haiku.haikudepotserver.dataobjects.MediaType.getByExtension(context, format).get(),
                        null);

                if(hvifPkgIcon.isPresent()) {
                    byte[] data = hvifPkgIcon.get().getPkgIconImage().get().getData();
                    response.setHeader(HttpHeaders.CONTENT_LENGTH,Integer.toString(data.length));
                    response.setContentType(org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE);
                    response.setDateHeader(HttpHeaders.LAST_MODIFIED, pkg.get().getModifyTimestampSecondAccuracy().getTime());

                    if(requestMethod == RequestMethod.GET) {
                        response.getOutputStream().write(data);
                    }
                }
                else {
                    throw new PkgIconNotFound();
                }
                break;

            case org.haiku.haikudepotserver.dataobjects.MediaType.EXTENSION_PNG:

                if(null==size) {
                    throw new IllegalArgumentException("the size must be provided when requesting a PNG");
                }

                size = normalizeSize(size);
                Optional<byte[]> pngImageData = renderedPkgIconRepository.render(size, context, pkg.get());

                if(!pngImageData.isPresent()) {
                    if((null==fallback) || !fallback) {
                        throw new PkgIconNotFound();
                    }

                    handleGenericHeadOrGet(requestMethod, response, size, true);
                }
                else {
                    byte[] data = pngImageData.get();
                    response.setHeader(HttpHeaders.CONTENT_LENGTH,Integer.toString(data.length));
                    response.setContentType(MediaType.PNG.toString());
                    response.setDateHeader(HttpHeaders.LAST_MODIFIED, pkg.get().getModifyTimestampSecondAccuracy().getTime());

                    if(requestMethod == RequestMethod.GET) {
                        OutputStream outputStream = response.getOutputStream();
                        outputStream.write(data);
                        outputStream.flush();
                    }
                }
                break;

            default:
                throw new IllegalStateException("unexpected format; " + format);

        }

    }

    // these are the various errors that can arise in supplying or providing a package icon.

    @ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="the package name must be supplied")
    public class MissingPkgName extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason="the format must be supplied and must (presently) be 'png'")
    public class MissingOrBadFormat extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package was unable to found")
    public class PkgNotFound extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package icon was unable to found")
    public class PkgIconNotFound extends RuntimeException {}

}
