/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.web;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import net.jawr.web.resource.bundle.factory.util.ServletContextAware;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.MediaType;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import javax.annotation.Resource;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;

/**
 * <p>If somebody makes a query such as "/apr" then the system should search for that as a package
 * and navigate to the user interface for it.</p>
 */

@Controller
public class FallbackController {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgOrchestrationService.class);

    public final static String KEY_TERM = "term";

    public final static String VALUE_FAVICON = "favicon";

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Value("${baseurl}")
    private String baseUrl;

    @Autowired
    private ServletContext servletContext;

    private String termDebug(String term) {
        if(term.length() > 64) {
            return term.substring(0,64) + "...";
        }

        return term;
    }

    private void streamFavicon(HttpServletResponse response) throws IOException {
        Preconditions.checkState(null!=servletContext, "the servlet context must be supplied");
        response.setContentType(com.google.common.net.MediaType.ICO.toString());

        try (InputStream inputStream = servletContext.getResourceAsStream("/img/favicon.ico")) {
            if(null==inputStream) {
                throw new IllegalStateException("unable to find the favicon resource");
            }

            ByteStreams.copy(inputStream, response.getOutputStream());
        }
    }

    @RequestMapping(value = "/{"+KEY_TERM+"}", method = RequestMethod.GET)
    public void fallback(
            HttpServletResponse response,
            @PathVariable(value = KEY_TERM) String term)
            throws IOException {

        ObjectContext context = serverRuntime.getContext();

        if(term.equalsIgnoreCase(VALUE_FAVICON)) {
            streamFavicon(response);
        }
        else {

            Optional<PkgVersion> pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkgName(
                    context,
                    term);

            if (pkgVersionOptional.isPresent()) {
                UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(baseUrl).pathSegment("#", "pkg");
                pkgVersionOptional.get().appendPathSegments(builder);
                UriComponents uriComponents = builder.build();

                response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
                response.setHeader(HttpHeaders.LOCATION, uriComponents.toUriString());

                PrintWriter w = response.getWriter();
                w.format("redirecting to; %s", uriComponents.toUriString());
                w.flush();

                LOGGER.info("did redirect to a package for; {}", term);
            } else {
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);

                PrintWriter w = response.getWriter();
                w.format("unable to find an entity for; %s", termDebug(term));
                w.flush();

                LOGGER.info("did not find a package for; {}", termDebug(term));
            }
        }

    }

}
