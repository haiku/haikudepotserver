/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import com.googlecode.jsonrpc4j.Base64;
import org.apache.cayenne.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This authentication filter will catch authentication data on in-bound HTTP requests and will delegate the
 * authentication.  It handles basic authentication and so should be used over an HTTPS connection.</p>
 *
 * <p>A successful authentication will result in the authenticated user being registered into a thread-local and
 * this user can be accessed by other code which is executing in the same thread.</p>
 *
 * <p>This filter does not present back to the user an HTTP 401 (Authorization Required) as is the norm with
 * basic authentication method; it will simply fail the authentication and there will be no authenticated user
 * in the current request-response cycle.</p>
 *
 * <p>For some URLs (those starting with "/secured/"), it is also possible for the filter to look for a
 * bearer token on the parameters.  This only works on a GET request.</p>
 */

public class AuthenticationFilter implements Filter {

    protected static Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

    private static Pattern PATTERN_AUTHORIZATION_HEADER = Pattern.compile("^([A-Za-z0-9]+)\\s+(.+)$");

    private static String PREFIX_PATH_SECURED = "/secured/";

    private static String PARAM_BEARER_TOKEN = "hdsbtok";

    @Resource
    AuthenticationService authenticationService;

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String authorizationHeader = httpRequest.getHeader(HttpHeaders.AUTHORIZATION);
        Optional<ObjectId> authenticatedUserObjectId = Optional.absent();

        if(!Strings.isNullOrEmpty(authorizationHeader)) {

            Matcher authorizationMatcher = PATTERN_AUTHORIZATION_HEADER.matcher(authorizationHeader);

            if(authorizationMatcher.matches()) {

                switch(authorizationMatcher.group(1)) {

                    case "Basic":
                        byte[] usernamePasswordBytes = Base64.decode(authorizationMatcher.group(2));

                        if (null != usernamePasswordBytes && usernamePasswordBytes.length >= 3) {
                            List<String> parts = Lists.newArrayList(Splitter.on(":").split(new String(usernamePasswordBytes, Charsets.UTF_8)));

                            if (2 == parts.size()) {
                                authenticatedUserObjectId = authenticationService.authenticateByNicknameAndPassword(parts.get(0), parts.get(1));
                            } else {
                                logger.warn("attempt to process an authorization header, but the username password is malformed; is not <username>:<password>");
                            }
                        } else {
                            logger.warn("attempt to process an authorization header, but the username password is malformed; being decoded from base64");
                        }
                        break;

                    case "Bearer":
                        authenticatedUserObjectId = authenticationService.authenticateByToken(authorizationMatcher.group(2));
                        break;

                    default:
                        logger.warn("attempt to process an authorization header, but the authorization method {} is unknown :. ignoring", authorizationMatcher.group(1));
                        break;

                }
            }
            else {
                logger.warn("attempt to process an authorization header, but it is malformed :. ignoring");
            }
        }

        // if the user was not authenticated on the header, under certain circumstances, it may be possible for
        // the authentication to occur based on a parameter of the GET request (in the query).

        if(!authenticatedUserObjectId.isPresent() && httpRequest.getMethod().equals("GET")) {
            String filterPathInfo = httpRequest.getRequestURI().substring(httpRequest.getContextPath().length());

            if(filterPathInfo.startsWith(PREFIX_PATH_SECURED)) {
                String param = httpRequest.getParameter(PARAM_BEARER_TOKEN);

                if (!Strings.isNullOrEmpty(param)) {
                    authenticatedUserObjectId = authenticationService.authenticateByToken(param);
                }
            }
        }

        // now continue with the rest of the servlet filter chain, keeping the thread local

        try {
            AuthenticationHelper.setAuthenticatedUserObjectId(authenticatedUserObjectId);
            chain.doFilter(request,response);
        }
        finally {
            AuthenticationHelper.setAuthenticatedUserObjectId(Optional.<ObjectId>absent());
        }
    }

    @Override
    public void destroy() {
    }

}
