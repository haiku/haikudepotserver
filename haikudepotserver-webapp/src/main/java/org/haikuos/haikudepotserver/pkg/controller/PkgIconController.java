/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.controller;

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
import org.haikuos.haikudepotserver.dataobjects.PkgIconImage;
import org.haikuos.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.ServletContextAware;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * <p>This controller vends the package icon.  This may be provided by data stored in the database, or it may be
 * deferring to the default icon.  This controller is also able to take an HTTP PUT request that is able to
 * update a packages icon.  This is not done using JSON-RPC because the binary nature of the data makes transport
 * of the data in JSON impractical.</p>
 */

@Controller
@RequestMapping("/pkgicon")
public class PkgIconController extends AbstractController implements ServletContextAware {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgIconController.class);

    public final static String KEY_PKGNAME = "pkgname";
    public final static String KEY_FORMAT = "format";
    public final static String KEY_SIZE = "s";
    public final static String KEY_FALLBACK = "f";

    @Resource
    private ServerRuntime serverRuntime;

    private ServletContext servletContext;

    public void setServletContext(ServletContext servletContext) {
        this.servletContext = servletContext;
    }

    private LoadingCache<Integer, byte[]> genericBitmapIcons = CacheBuilder.newBuilder()
            .maximumSize(10)
            .build(
                    new CacheLoader<Integer, byte[]>() {
                        public byte[] load(@SuppressWarnings("NullableProblems") Integer size) {
                            String resource = String.format("img/generic%d.png", size);

                            try (InputStream inputStream = servletContext.getResourceAsStream(resource)) {

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
            LOGGER.warn("attempt to request icon of format '{}', but this format was not recognized", format);
            throw new MissingOrBadFormat();
        }

        Optional<Pkg> pkg = Pkg.getByName(context, pkgName); // cached

        if(!pkg.isPresent()) {
            LOGGER.debug("request for icon for package '{}', but no such package was able to be found", pkgName);
            throw new PkgNotFound();
        }

        Optional<PkgIconImage> pkgIconImageOptional = PkgIconImage.getForPkg(
                context,
                pkg.get(),
                mediaTypeOptional.get(),
                size);

        if(!pkgIconImageOptional.isPresent()) {

            // if there is no icon, under very specific circumstances, it may be possible to provide one.

            if(
                    (null!=fallback)
                            && fallback
                            && mediaTypeOptional.get().getCode().equals(MediaType.PNG.toString())
                            && (null!=size)
                            && (16==size || 32==size)) {

                byte[] data = genericBitmapIcons.getUnchecked(size);
                response.setHeader(HttpHeaders.CONTENT_LENGTH,Integer.toString(data.length));
                response.setHeader(HttpHeaders.CACHE_CONTROL,"public, max-age=3600");
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

            byte[] data = pkgIconImageOptional.get().getData();

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

    @ResponseStatus(value= HttpStatus.BAD_REQUEST, reason="the package name must be supplied")
    public class MissingPkgName extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.UNSUPPORTED_MEDIA_TYPE, reason="the format must be supplied and must (presently) be 'png'")
    public class MissingOrBadFormat extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package was unable to found")
    public class PkgNotFound extends RuntimeException {}

    @ResponseStatus(value= HttpStatus.NOT_FOUND, reason="the requested package icon was unable to found")
    public class PkgIconNotFound extends RuntimeException {}

}
