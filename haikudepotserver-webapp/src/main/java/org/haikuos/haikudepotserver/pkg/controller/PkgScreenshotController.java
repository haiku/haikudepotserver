/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.controller;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgScreenshot;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.pkg.model.BadPkgScreenshotException;
import org.haikuos.haikudepotserver.pkg.model.SizeLimitReachedException;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.ByteCounterOutputStream;
import org.haikuos.haikudepotserver.support.NoOpOutputStream;
import org.haikuos.haikudepotserver.support.web.AbstractController;
import org.haikuos.haikudepotserver.web.controller.WebResourceGroupController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/pkgscreenshot")
public class PkgScreenshotController extends AbstractController {

    protected static Logger logger = LoggerFactory.getLogger(WebResourceGroupController.class);

    public final static String HEADER_SCREENSHOTCODE = "X-HaikuDepotServer-ScreenshotCode";

    public final static String KEY_PKGNAME = "pkgname";
    public final static String KEY_SCREENSHOTCODE = "code";
    public final static String KEY_FORMAT = "format";
    public final static String KEY_TARGETWIDTH = "tw";
    public final static String KEY_TARGETHEIGHT = "th";

    protected static int SCREENSHOT_SIDE_LIMIT = 1500;

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PkgOrchestrationService pkgService;

    @Resource
    AuthorizationService authorizationService;

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

        ObjectContext context = serverRuntime.getContext();
        Optional<PkgScreenshot> screenshotOptional = PkgScreenshot.getByCode(context, screenshotCode);

        if(!screenshotOptional.isPresent()) {
            throw new ScreenshotNotFound();
        }

        response.setContentType(MediaType.PNG.toString());
        response.setHeader(HttpHeaders.CACHE_CONTROL, "max-age=3600");

        response.setDateHeader(
                HttpHeaders.LAST_MODIFIED,
                screenshotOptional.get().getPkg().getModifyTimestampSecondAccuracy().getTime());

        switch(requestMethod) {
            case HEAD:
                ByteCounterOutputStream byteCounter = new ByteCounterOutputStream(new NoOpOutputStream());

                pkgService.writePkgScreenshotImage(
                        byteCounter,
                        context,
                        screenshotOptional.get(),
                        targetWidth,
                        targetHeight);

                response.setHeader(HttpHeaders.CONTENT_LENGTH, Long.toString(byteCounter.getCounter()));

                break;

            case GET:
                pkgService.writePkgScreenshotImage(
                        response.getOutputStream(),
                        context,
                        screenshotOptional.get(),
                        targetWidth,
                        targetHeight);
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

        ObjectContext context = serverRuntime.getContext();
        Optional<PkgScreenshot> screenshotOptional = PkgScreenshot.getByCode(context, screenshotCode);

        if(!screenshotOptional.isPresent()) {
            throw new ScreenshotNotFound();
        }

        byte[] data = screenshotOptional.get().getPkgScreenshotImage().get().getData();
        org.haikuos.haikudepotserver.dataobjects.MediaType mediaType = screenshotOptional.get().getPkgScreenshotImage().get().getMediaType();

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
                        screenshotOptional.get().getPkg().getName(),
                        screenshotOptional.get().getCode(),
                        extension));

        response.getOutputStream().write(data);
    }

    /**
     * <p>This handler will take-up an HTTP PUT that provides a new screenshot for the package.</p>
     */

    @RequestMapping(value = "/{"+KEY_PKGNAME+"}/add", method = RequestMethod.POST)
    public void handleAdd(
            HttpServletRequest request,
            HttpServletResponse response,
            @RequestParam(value = KEY_FORMAT, required = true) String format,
            @PathVariable(value = KEY_PKGNAME) String pkgName) throws IOException {

        if(Strings.isNullOrEmpty(pkgName) || !Pkg.NAME_PATTERN.matcher(pkgName).matches()) {
            throw new MissingPkgName();
        }

        if(Strings.isNullOrEmpty(format) || !"png".equals(format)) {
            throw new MissingOrBadFormat();
        }

        ObjectContext context = serverRuntime.getContext();

        Optional<Pkg> pkg = Pkg.getByName(context, pkgName);

        if(!pkg.isPresent()) {
            throw new PkgNotFound();
        }

        // check the authorization

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!authorizationService.check(context, user.orNull(), pkg.get(), Permission.PKG_EDITSCREENSHOT)) {
            logger.warn("attempt to add a pkg screenshot, but there is no user present or that user is not able to edit the pkg");
            throw new PkgAuthorizationFailure();
        }

        String screenshotCode;

        try {
            screenshotCode = pkgService.storePkgScreenshotImage(
                    request.getInputStream(),
                    context,
                    pkg.get()).getCode();
        }
        catch(SizeLimitReachedException sizeLimit) {
            logger.warn("attempt to load in a screenshot larger than the size limit");
            throw new MissingOrBadFormat();
        }
        catch(BadPkgScreenshotException badIcon) {
            throw new MissingOrBadFormat();
        }

        context.commitChanges();

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
