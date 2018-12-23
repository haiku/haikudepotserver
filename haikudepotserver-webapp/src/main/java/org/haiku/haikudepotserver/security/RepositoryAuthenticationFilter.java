/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.net.HttpHeaders;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.support.AbstractDataObject;
import org.haiku.haikudepotserver.repository.controller.RepositoryController;
import org.haiku.haikudepotserver.security.model.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This filter will enforce authentication for certain repository actions.
 * </p>
 */

public class RepositoryAuthenticationFilter implements Filter {

    protected static final Logger LOGGER = LoggerFactory.getLogger(RepositoryAuthenticationFilter.class);

    private final static Pattern PATTERN_REPOSITORY_PREFIX =
            Pattern.compile("^/" + RepositoryController.SEGMENT_REPOSITORY
                    + "/(" + AbstractDataObject.CODE_PATTERN_STRING + ")/.+$");

    private final Predicate<String> pathMatchingPredicate;

    private final ServerRuntime serverRuntime;

    private final AuthenticationService authenticationService;

    /**
     * @param pathMatchingPredicate is called with the path of the request; only those that return true will be checked
     *                              for authentication.
     */

    public RepositoryAuthenticationFilter(
            ServerRuntime serverRuntime,
            AuthenticationService authenticationService,
            Predicate<String> pathMatchingPredicate) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.authenticationService = Preconditions.checkNotNull(authenticationService);
        this.pathMatchingPredicate = Preconditions.checkNotNull(pathMatchingPredicate);
    }

    @Override
    public void init(FilterConfig filterConfig) {
        // init is not required
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain filterChain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        if (pathMatchingPredicate.apply(StringUtils.trimToEmpty(httpRequest.getServletPath()))) {
            HttpServletResponse httpResponse = (HttpServletResponse) response;

            Optional<String> repositoryCodeOptional = tryGetRepositoryCodeFromPath(httpRequest);

            if (repositoryCodeOptional.isPresent()) {
                doFilter(repositoryCodeOptional.get(), httpRequest, httpResponse, filterChain);
            } else {
                LOGGER.info(
                        "the repository was not able to be derived from the path [{}]",
                        httpRequest.getServletPath());
                httpResponse.setStatus(HttpStatus.BAD_REQUEST.value());
            }
        } else {
            filterChain.doFilter(request, response);
        }
    }

    private void doFilter(
            String repositoryCode,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            FilterChain filterChain) throws IOException, ServletException {
        ObjectContext context = serverRuntime.newContext();
        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, repositoryCode);

        if (repositoryOptional.isPresent()) {
            doFilter(repositoryOptional.get(), httpRequest, httpResponse, filterChain);
        } else {
            LOGGER.info("the repository [{}] was not able to be found", repositoryCode);
            httpResponse.setStatus(HttpStatus.NOT_FOUND.value());
        }
    }


    private void doFilter(
            Repository repository,
            HttpServletRequest httpRequest,
            HttpServletResponse httpResponse,
            FilterChain filterChain) throws IOException, ServletException {

        String authorizationHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);

        if (hasSuccessfulAuthentication(repository, authorizationHeader)) {
            filterChain.doFilter(httpRequest, httpResponse);
        } else {
            LOGGER.info("authentication failure for repository [{}]", repository);
            httpResponse.setStatus(HttpStatus.UNAUTHORIZED.value());
        }
    }

    private Optional<String> tryGetRepositoryCodeFromPath(HttpServletRequest httpRequest) {
     return Optional
             .ofNullable(httpRequest.getServletPath())
             .map(PATTERN_REPOSITORY_PREFIX::matcher)
             .filter(Matcher::matches)
             .map(m -> m.group(1));
    }

    private boolean hasSuccessfulAuthentication(
            Repository repository,
            String authorizationHeader) {
        if (StringUtils.isBlank(repository.getPasswordHash())) {
            return true;
        }

        return authenticationService.tryExtractCredentialsFromBasicAuthorizationHeader(authorizationHeader)
                .map(Pair::getRight)
                .map(p -> authenticationService.hashPassword(repository.getPasswordSalt(), p))
                .filter(h -> h.equals(repository.getPasswordHash()))
                .isPresent();
    }

    @Override
    public void destroy() {
        // init is not required
    }
}
