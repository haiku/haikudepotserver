/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.web;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import com.googlecode.jsonrpc4j.Base64;
import org.apache.cayenne.ObjectId;
import org.haikuos.haikudepotserver.services.AuthenticationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;

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
 */

public class AuthenticationFilter implements Filter {

    protected static Logger logger = LoggerFactory.getLogger(AuthenticationFilter.class);

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
            if(authorizationHeader.startsWith("Basic ")) {
                byte[] usernamePasswordBytes = Base64.decode(authorizationHeader.substring(6));

                if(null!=usernamePasswordBytes && usernamePasswordBytes.length >= 3) {
                    List<String> parts = Lists.newArrayList(Splitter.on(":").split(new String(usernamePasswordBytes, Charsets.UTF_8)));

                    if(2==parts.size()) {
                        authenticatedUserObjectId = authenticationService.authenticate(parts.get(0), parts.get(1));
                    }
                    else {
                        logger.warn("attempt to process an authorization header, but the username password is malformed; is not <username>:<password>");
                    }
                }
                else {
                    logger.warn("attempt to process an authorization header, but the username password is malformed; being decoded from base64");
                }
            }
            else {
                logger.warn("attempt to process an authorization header, but it is not basic authentication :. ignoring");
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
