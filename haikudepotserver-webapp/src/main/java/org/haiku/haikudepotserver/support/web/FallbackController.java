/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import jakarta.mail.internet.MimeUtility;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.collections4.ComparatorUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySource;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>If somebody makes a query such as "/apr" then the system should search for that as a package
 * and navigate to the user interface for it.</p>
 */

// TODO (andponlin) This concept and way of responding is quite dated; it could be rewritten.

@Controller
public class FallbackController {

    private enum FallbackType {
        FAVICON,
        APPLE_TOUCH,
        PKG
    }

    protected static Logger LOGGER = LoggerFactory.getLogger(FallbackController.class);

    private final static String KEY_TERM = "term";

    private final static Pattern PATTERN_FAVICON = Pattern.compile("^/favicon(\\.ico)?$");
    private final static Pattern PATTERN_PKG = Pattern.compile("^/([a-z0-9][a-z0-9._]{0,254})$");
    private final static Pattern PATTERN_APPLE_TOUCH = Pattern.compile("^/apple-touch-icon-\\d+x\\d+.png$");

    private final ServerRuntime serverRuntime;
    private final PkgService pkgService;
    private final RepositoryService repositoryService;
    private final String baseUrl;
    private final String defaultArchitectureCode;

    private final String defaultRepositoryCode;

    @Autowired(required = false)
    private ServletContext servletContext;

    private final Resource faviconResource;

    public FallbackController(
            ServerRuntime serverRuntime,
            PkgService pkgService,
            RepositoryService repositoryService,
            @Value("classpath:/img/favicon.ico") Resource faviconResource,
            @Value("${hds.architecture.default.code}") String defaultArchitectureCode,
            @Value("${hds.base-url}") String baseUrl,
            @Value("${hds.repository.default.code}") String defaultRepositoryCode) {
        this.serverRuntime = serverRuntime;
        this.pkgService = pkgService;
        this.repositoryService = repositoryService;
        this.baseUrl = baseUrl;
        this.defaultArchitectureCode = defaultArchitectureCode;
        this.faviconResource = faviconResource;
        this.defaultRepositoryCode = defaultRepositoryCode;
    }

    private String termDebug(String term) {
        if (term.length() > 64) {
            return term.substring(0,64) + "...";
        }

        return term;
    }

    private void redirectToPkg(HttpServletResponse response, String term) throws IOException {
        ObjectContext context = serverRuntime.newContext();

        Optional<PkgVersion> pkgVersionOptional = tryGetPkgVersion(context, term);

        if (pkgVersionOptional.isPresent()) {
            UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).pathSegment("#!", "pkg");
            pkgVersionOptional.get().appendPathSegments(builder);
            UriComponents uriComponents = builder.build();

            response.setStatus(HttpServletResponse.SC_MOVED_TEMPORARILY);
            response.setHeader(HttpHeaders.LOCATION, MimeUtility.encodeText(uriComponents.toUriString()));

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

    private void streamFavicon(RequestMethod method, HttpServletResponse response) throws IOException {
        Preconditions.checkState(null!=servletContext, "the servlet context must be supplied");
        response.setContentType(com.google.common.net.MediaType.ICO.toString());

        try (InputStream inputStream = faviconResource.getInputStream()) {
            if (method != RequestMethod.HEAD) {
                inputStream.transferTo(response.getOutputStream());
            }
        }
    }

    private static Optional<FallbackType> tryGetFallbackType(String term) {
        if (PATTERN_FAVICON.matcher(term).matches()) {
            return Optional.of(FallbackType.FAVICON);
        }
        if (PATTERN_APPLE_TOUCH.matcher(term).matches()) {
            return Optional.of(FallbackType.APPLE_TOUCH);
        }
        if (PATTERN_PKG.matcher(term).matches()) {
            return Optional.of(FallbackType.PKG);
        }
        return Optional.empty();
    }

    private Optional<PkgVersion> tryGetPkgVersion(ObjectContext context, String term) {
        return Optional.ofNullable(StringUtils.trimToNull(term))
                .flatMap(t -> Pkg.tryGetByName(context, t))
                .flatMap(p -> findBestPkgVersion(context, p));
    }

    private Optional<PkgVersion> findBestPkgVersion(ObjectContext context, Pkg pkg) {
        List<RepositorySource> sortedRepositorySources = repositoryService.getRepositoriesForPkg(context, pkg)
                .stream()
                .flatMap(r -> r.getRepositorySources().stream())
                .sorted(ComparatorUtils.chainedComparator(
                        Comparator
                                .comparing((RepositorySource rs) -> rs.getRepository().getCode().equals(defaultRepositoryCode))
                                .reversed(),
                        Comparator
                                .comparing((RepositorySource rs) -> rs.getCode().equals(defaultArchitectureCode))
                                .reversed(),
                        Comparator.comparing(_RepositorySource::getCode)))
                .toList();

        return sortedRepositorySources.stream()
                .map(rs -> pkgService.getLatestPkgVersionForPkg(context, pkg, rs))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .findFirst();
    }

    @RequestMapping(value = "/{"+KEY_TERM+"}", method = { RequestMethod.GET, RequestMethod.HEAD } )
    public void fallback(
            RequestMethod method,
            HttpServletResponse response,
            HttpServletRequest request)
            throws IOException {

                String requestUri = request.getRequestURI();
        Optional<FallbackType> typeOptional = tryGetFallbackType(requestUri);

        if (typeOptional.isPresent()) {
            switch (typeOptional.get()) {
                case APPLE_TOUCH -> {
                    LOGGER.debug("unhandled apple touch icon -> 404; {}", requestUri);
                    response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                }
                case FAVICON -> streamFavicon(method, response);
                case PKG -> {
                    Matcher matcher = PATTERN_PKG.matcher(requestUri);

                    if (!matcher.find()) {
                        throw new IllegalStateException("bad request uri [%s]".formatted(requestUri));
                    }

                    redirectToPkg(response, matcher.group(1));
                }
            }
        } else {
            LOGGER.warn("unknown fallback [{}]", requestUri);
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        }
    }

    /**
     * <p>This is a simple {@link RequestMatcher} that will match the endpoints above that are for handling a fallback.</p>
     */

    public static class FallbackRequestMatcher implements RequestMatcher {
        @Override
        public boolean matches(HttpServletRequest request) {
            return tryGetFallbackType(request.getRequestURI()).isPresent();
        }
    }

}
