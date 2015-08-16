/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgIcon;
import org.haikuos.haikudepotserver.pkg.RenderedPkgIconRepository;
import org.haikuos.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
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

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgIconController.class);

    public final static String SEGMENT_PKGICON = "pkgicon";
    public final static String SEGMENT_GENERICPKGICON = "genericpkgicon.png";

    public final static String KEY_PKGNAME = "pkgname";
    public final static String KEY_FORMAT = "format";
    public final static String KEY_SIZE = "s";
    public final static String KEY_FALLBACK = "f";

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private RenderedPkgIconRepository renderedPkgIconRepository;

    private long startupMillis = System.currentTimeMillis();

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

    private void handleHeadOrGet(
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

        if(Strings.isNullOrEmpty(pkgName) || !Pkg.NAME_PATTERN.matcher(pkgName).matches()) {
            throw new MissingPkgName();
        }

        ObjectContext context = serverRuntime.getContext();
        Optional<Pkg> pkg = Pkg.getByName(context, pkgName); // cached

        if(!pkg.isPresent()) {
            LOGGER.debug("request for icon for package '{}', but no such package was able to be found", pkgName);
            throw new PkgNotFound();
        }

        switch(format) {

            case org.haikuos.haikudepotserver.dataobjects.MediaType.EXTENSION_HAIKUVECTORICONFILE:
                Optional<PkgIcon> hvifPkgIcon = pkg.get().getPkgIcon(
                        org.haikuos.haikudepotserver.dataobjects.MediaType.getByExtension(context, format).get(),
                        null);

                if(hvifPkgIcon.isPresent()) {
                    byte[] data = hvifPkgIcon.get().getPkgIconImage().get().getData();
                    response.setHeader(HttpHeaders.CONTENT_LENGTH,Integer.toString(data.length));
                    response.setContentType(org.haikuos.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE);
                    response.setDateHeader(HttpHeaders.LAST_MODIFIED, pkg.get().getModifyTimestampSecondAccuracy().getTime());

                    if(requestMethod == RequestMethod.GET) {
                        response.getOutputStream().write(data);
                    }
                }
                else {
                    throw new PkgIconNotFound();
                }
                break;

            case org.haikuos.haikudepotserver.dataobjects.MediaType.EXTENSION_PNG:

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

    @RequestMapping(value = "/" + SEGMENT_PKGICON + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}", method = RequestMethod.HEAD)
    public void handleHead(
            HttpServletResponse response,
            @RequestParam(value = KEY_SIZE, required = false) Integer size,
            @PathVariable(value = KEY_FORMAT) String format,
            @PathVariable(value = KEY_PKGNAME) String pkgName,
            @RequestParam(value = KEY_FALLBACK, required = false) Boolean fallback)
            throws IOException {
        handleHeadOrGet(
                RequestMethod.HEAD,
                response,
                size,
                format,
                pkgName,
                fallback);
    }

    @RequestMapping(value = "/" + SEGMENT_PKGICON + "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}", method = RequestMethod.GET)
    public void handleGet(
            HttpServletResponse response,
            @RequestParam(value = KEY_SIZE, required = false) Integer size,
            @PathVariable(value = KEY_FORMAT) String format,
            @PathVariable(value = KEY_PKGNAME) String pkgName,
            @RequestParam(value = KEY_FALLBACK, required = false) Boolean fallback)
    throws IOException {
        handleHeadOrGet(
                RequestMethod.GET,
                response,
                size,
                format,
                pkgName,
                fallback);
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
