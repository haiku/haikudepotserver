/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.controller;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgIcon;
import org.haikuos.haikudepotserver.pkg.PkgService;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.support.ByteCounterOutputStream;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.NoOpOutputStream;
import org.haikuos.haikudepotserver.support.web.AbstractController;
import org.haikuos.haikudepotserver.web.controller.WebResourceGroupController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>This controller vends the package icon.  This may be provided by data stored in the database, or it may be
 * deferring to the default icon.  This controller is also able to take an HTTP PUT request that is able to
 * update a packages icon.  This is not done using JSON-RPC because the binary nature of the data makes transport
 * of the data in JSON impractical.</p>
 */

@Controller
@RequestMapping("/pkgicon")
public class PkgIconController extends AbstractController {

    protected static Logger logger = LoggerFactory.getLogger(WebResourceGroupController.class);

    public final static String KEY_PKGNAME = "pkgname";
    public final static String KEY_FORMAT = "format";
    public final static String KEY_SIZE = "s";
    public final static String KEY_FALLBACK = "f";

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    PkgService pkgService;

    @Resource
    AuthorizationService authorizationService;

    private LoadingCache<Integer, byte[]> genericBitmapIcons = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build(
                    new CacheLoader<Integer, byte[]>() {
                        public byte[] load(Integer size) {
                            String resource = String.format("/img/generic/generic%d.png", size);
                            InputStream inputStream = null;

                            try {
                                inputStream = this.getClass().getResourceAsStream(resource);

                                if(null==inputStream) {
                                    throw new IllegalStateException(String.format("the resource; %s was not able to be found, but should be in the application build product", resource));
                                }
                                else {
                                    return ByteStreams.toByteArray(inputStream);
                                }
                            }
                            catch(IOException ioe) {
                                throw new IllegalStateException("unable to read the generic icon",ioe);
                            }
                            finally {
                                Closeables.closeQuietly(inputStream);
                            }
                        }
                    });

    private void handleHeadOrGet(
            RequestMethod requestMethod,
            HttpServletResponse response,
            Integer size,
            String format,
            String pkgName,
            Boolean fallback)
            throws IOException {

        if(Strings.isNullOrEmpty(pkgName) || !Pkg.NAME_PATTERN.matcher(pkgName).matches()) {
            throw new MissingPkgName();
        }

        ObjectContext context = serverRuntime.getContext();
        Optional<org.haikuos.haikudepotserver.dataobjects.MediaType> mediaTypeOptional
                = org.haikuos.haikudepotserver.dataobjects.MediaType.getByExtension(context, format);

        if(!mediaTypeOptional.isPresent()) {
            logger.warn("attempt to request icon of format '{}', but this format was not recognized",format);
            throw new MissingOrBadFormat();
        }

        Optional<Pkg> pkg = Pkg.getByName(context, pkgName);

        if(!pkg.isPresent()) {
            logger.info("request for icon for package '{}', but no such package was able to be found",pkgName);
            throw new PkgNotFound();
        }

        ByteCounterOutputStream byteCounter = new ByteCounterOutputStream(new NoOpOutputStream());
        Optional<PkgIcon> pkgIconOptional = pkg.get().getPkgIcon(mediaTypeOptional.get(), size);

        if(!pkgIconOptional.isPresent()) {

            // if there is no icon, under very specific circumstances, it may be possible to provide one.

            if(
                    (null!=fallback)
                            && fallback.booleanValue()
                            && mediaTypeOptional.get().getCode().equals(MediaType.PNG.toString())
                            && (null!=size)
                            && (16==size || 32==size)) {

                byte[] data = genericBitmapIcons.getUnchecked(size);
                response.setHeader(HttpHeaders.CONTENT_LENGTH,Integer.toString(data.length));
                response.setContentType(mediaTypeOptional.get().getCode());

                if(requestMethod == RequestMethod.GET) {
                    response.getOutputStream().write(data);
                }

            }
            else {
                throw new PkgIconNotFound();
            }

        }
        else {

            byte[] data = pkgIconOptional.get().getPkgIconImage().get().getData();

            response.setHeader(HttpHeaders.CONTENT_LENGTH,Integer.toString(data.length));
            response.setContentType(mediaTypeOptional.get().getCode());
            response.setDateHeader(HttpHeaders.LAST_MODIFIED, pkg.get().getModifyTimestampSecondAccuracy().getTime());

            if(requestMethod == RequestMethod.GET) {
                response.getOutputStream().write(data);
            }
        }
    }

    @RequestMapping(value = "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}", method = RequestMethod.HEAD)
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

    @RequestMapping(value = "/{"+KEY_PKGNAME+"}.{"+KEY_FORMAT+"}", method = RequestMethod.GET)
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

    @ResponseStatus(value= HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason="the size must be 16 or 32")
    public class BadSize extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="the package name must be supplied")
    public class MissingPkgName extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason="the format must be supplied and must (presently) be 'png'")
    public class MissingOrBadFormat extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package was unable to found")
    public class PkgNotFound extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package icon was unable to found")
    public class PkgIconNotFound extends RuntimeException {}

}
