/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotOptimizationJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotService;
import org.haiku.haikudepotserver.pkg.model.SizeLimitReachedException;
import org.haiku.haikudepotserver.security.model.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.ByteCounterOutputStream;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Controller
@RequestMapping(value = {
        PkgScreenshotController.SEGMENT_SCREENSHOT,
        PkgScreenshotController.SEGMENT_SCREENSHOT_LEGACY // TODO; remove
})
public class PkgScreenshotController extends AbstractController {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgScreenshotController.class);

    final static String SEGMENT_SCREENSHOT = "__pkgscreenshot";
    final static String SEGMENT_SCREENSHOT_LEGACY = "pkgscreenshot";

    final static String HEADER_SCREENSHOTCODE = "X-HaikuDepotServer-ScreenshotCode";

    private final static String KEY_PKGNAME = "pkgname";
    private final static String KEY_SCREENSHOTCODE = "code";
    private final static String KEY_FORMAT = "format";
    private final static String KEY_TARGETWIDTH = "tw";
    private final static String KEY_TARGETHEIGHT = "th";

    private static int SCREENSHOT_SIDE_LIMIT = 1500;

    private ServerRuntime serverRuntime;
    private PkgScreenshotService pkgScreenshotService;
    private JobService jobService;
    private AuthorizationService authorizationService;

    public PkgScreenshotController(
            ServerRuntime serverRuntime,
            PkgScreenshotService pkgScreenshotService,
            JobService jobService,
            AuthorizationService authorizationService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgScreenshotService = Preconditions.checkNotNull(pkgScreenshotService);
        this.jobService = Preconditions.checkNotNull(jobService);
        this.authorizationService = Preconditions.checkNotNull(authorizationService);
    }

    private void handleHeadOrGet(
            RequestMethod requestMethod,
            HttpServletResponse response,
            Integer targetWidth,
            Integer targetHeight,
            String format,
            String screenshotCode)
            throws IOException {

        if(targetWidth <= 0 || targetWidth > SCREENSHOT_SIDE_LIMIT) {
            throw new BadSize();
        }

        if(targetHeight <= 0 || targetHeight > SCREENSHOT_SIDE_LIMIT) {
            throw new BadSize();
        }

        if(Strings.isNullOrEmpty(screenshotCode)) {
            throw new MissingScreenshotCode();
        }

        if(Strings.isNullOrEmpty(format) || !"png".equals(format)) {
            throw new MissingOrBadFormat();
        }

        ObjectContext context = serverRuntime.newContext();
        PkgScreenshot screenshot = PkgScreenshot.tryGetByCode(context, screenshotCode).orElseThrow(ScreenshotNotFound::new);

        response.setContentType(MediaType.PNG.toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600");

        response.setDateHeader(
                HttpHeaders.LAST_MODIFIED,
                screenshot.getPkg().getModifyTimestampSecondAccuracy().getTime());

        switch(requestMethod) {
            case HEAD:
                ByteCounterOutputStream byteCounter = new ByteCounterOutputStream(ByteStreams.nullOutputStream());
                pkgScreenshotService.writePkgScreenshotImage(byteCounter, context, screenshot, targetWidth, targetHeight);
                response.setHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(byteCounter.getCounter()));

                break;

            case GET:
                pkgScreenshotService.writePkgScreenshotImage(response.getOutputStream(), context, screenshot, targetWidth, targetHeight);
                break;

            default:
                throw new IllegalStateException("unhandled request method; "+requestMethod);
        }

    }

    @RequestMapping(value = "/{"+KEY_SCREENSHOTCODE+"}.{"+KEY_FORMAT+"}", method = RequestMethod.HEAD)
    public void handleHead(
            HttpServletResponse response,
            @RequestParam(value = KEY_TARGETWIDTH) Integer targetWidth,
            @RequestParam(value = KEY_TARGETHEIGHT) Integer targetHeight,
            @PathVariable(value = KEY_FORMAT) String format,
            @PathVariable(value = KEY_SCREENSHOTCODE) String screenshotCode)
            throws IOException {

        handleHeadOrGet(
                RequestMethod.HEAD,
                response,
                targetWidth,
                targetHeight,
                format,
                screenshotCode);

    }

    @RequestMapping(value = "/{"+KEY_SCREENSHOTCODE+"}.{"+KEY_FORMAT+"}", method = RequestMethod.GET)
    public void handleGet(
            HttpServletResponse response,
            @RequestParam(value = KEY_TARGETWIDTH, required = true) int targetWidth,
            @RequestParam(value = KEY_TARGETHEIGHT, required = true) int targetHeight,
            @PathVariable(value = KEY_FORMAT) String format,
            @PathVariable(value = KEY_SCREENSHOTCODE) String screenshotCode)
            throws IOException {

        handleHeadOrGet(
                RequestMethod.GET,
                response,
                targetWidth,
                targetHeight,
                format,
                screenshotCode);

    }

    /**
     * <p>This one downloads the raw data that is stored for a screenshot.</p>
     */

    @RequestMapping(value = "/{"+KEY_SCREENSHOTCODE+"}/raw", method = RequestMethod.GET)
    public void handleRawGet(
            HttpServletResponse response,
            @PathVariable(value = KEY_SCREENSHOTCODE) String screenshotCode)
            throws IOException {

        if(Strings.isNullOrEmpty(screenshotCode)) {
            throw new MissingScreenshotCode();
        }

        ObjectContext context = serverRuntime.newContext();
        PkgScreenshot screenshot = PkgScreenshot.tryGetByCode(context, screenshotCode).orElseThrow(ScreenshotNotFound::new);
        byte[] data = screenshot.tryGetPkgScreenshotImage().get().getData();
        org.haiku.haikudepotserver.dataobjects.MediaType mediaType = screenshot.tryGetPkgScreenshotImage().get().getMediaType();

        // TODO - find a better way to do this.
        String extension = null;

        if(mediaType.getCode().equals(MediaType.PNG.toString())) {
            extension = "png";
        }

        if(null==extension) {
            throw new IllegalStateException("the media type for the screenshot is not able to be converted into a file extension");
        }

        response.setContentLength(data.length);
        response.setContentType(mediaType.getCode());
        response.setHeader(
                HttpHeaders.CONTENT_DISPOSITION,
                String.format(
                        "attachment; filename=\"%s__%s.%s\"",
                        screenshot.getPkg().getName(),
                        screenshot.getCode(),
                        extension));

        response.getOutputStream().write(data);
    }

    /**
     * <p>This handler will take-up an HTTP POST that provides a new screenshot for the package.</p>
     */

    @RequestMapping(value = "/{"+KEY_PKGNAME+"}/add", method = RequestMethod.POST)
    public void handleAdd(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(value = KEY_FORMAT, required = true) String format,
            @PathVariable(value = KEY_PKGNAME) String pkgName) throws IOException {

        if(Strings.isNullOrEmpty(pkgName) || !Pkg.PATTERN_NAME.matcher(pkgName).matches()) {
            throw new MissingPkgName();
        }

        if(Strings.isNullOrEmpty(format) || !"png".equals(format)) {
            throw new MissingOrBadFormat();
        }

        ObjectContext context = serverRuntime.newContext();
        Pkg pkg = Pkg.tryGetByName(context, pkgName).orElseThrow(PkgNotFound::new);

        // check the authorization

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!authorizationService.check(context, user.orElse(null), pkg, Permission.PKG_EDITSCREENSHOT)) {
            LOGGER.warn("attempt to add a pkg screenshot, but there is no user present or that user is not able to edit the pkg");
            throw new PkgAuthorizationFailure();
        }

        String screenshotCode;

        try {
            screenshotCode = pkgScreenshotService.storePkgScreenshotImage(
                    request.getInputStream(), context, pkg, null).getCode();
        }
        catch(SizeLimitReachedException sizeLimit) {
            LOGGER.warn("attempt to load in a screenshot larger than the size limit");
            throw new MissingOrBadFormat();
        }
        catch(BadPkgScreenshotException badIcon) {
            throw new MissingOrBadFormat();
        }

        context.commitChanges();

        // trigger optimization of the screenshot image.

        jobService.submit(
                new PkgScreenshotOptimizationJobSpecification(screenshotCode),
                JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED);

        response.setHeader(HEADER_SCREENSHOTCODE, screenshotCode);
        response.setStatus(HttpServletResponse.SC_OK);
    }

    // these are the various errors that can arise in supplying or providing a package icon.

    @ResponseStatus(value= HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason="the target width or height must be greater than zero and less than the maximum")
    public class BadSize extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="the package name must be supplied")
    public class MissingPkgName extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="the screenshot code must be supplied")
    public class MissingScreenshotCode extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason="the format must be supplied and must (presently) be 'png'")
    public class MissingOrBadFormat extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package was unable to found")
    public class PkgNotFound extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested screenshot was unable to found")
    public class ScreenshotNotFound extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="the requested package cannot be edited by the authenticated user")
    public class PkgAuthorizationFailure extends RuntimeException {}

}
