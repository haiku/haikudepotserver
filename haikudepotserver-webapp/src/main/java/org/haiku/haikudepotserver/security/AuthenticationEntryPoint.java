/*
 * Copyright 2021-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.googlecode.jsonrpc4j.ErrorResolver;
import org.haiku.haikudepotserver.api1.support.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.WebAttributes;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class AuthenticationEntryPoint
        implements org.springframework.security.web.AuthenticationEntryPoint {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AuthenticationEntryPoint.class);

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        if (response.isCommitted()) {
            LOGGER.info("not sending back entry point response because response is already committed");
            return;
        }

        boolean acceptsJson = Optional.ofNullable(request.getHeader(HttpHeaders.ACCEPT))
                .map(MediaType::parseMediaTypes)
                .map(mts -> mts.stream().anyMatch(MediaType.APPLICATION_JSON::equalsTypeAndSubtype))
                .orElse(false);

        if (acceptsJson) {
            LOGGER.debug("responding entry point as json-rpc; {}", authException.getMessage());
            ErrorResolver.JsonError error = new ErrorResolver.JsonError(
                    Constants.ERROR_CODE_AUTHORIZATIONFAILURE,
                    "authorizationfailure",
                    null);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of("error", error, "jsonrpc", "2.0"));
            // ^ note missing "id" is bad, but possibly unavoidable here.
        } else {
            LOGGER.debug("responding entry point as not json-rpc; {}", authException.getMessage());
            request.setAttribute(WebAttributes.ACCESS_DENIED_403, authException);
            response.sendError(HttpStatus.UNAUTHORIZED.value(), HttpStatus.UNAUTHORIZED.getReasonPhrase());
        }

    }
}
