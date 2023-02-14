/*
 * Copyright 2021-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.haiku.haikudepotserver.api1.support.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.WebAttributes;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;

public class AccessDeniedHandler
        implements org.springframework.security.web.access.AccessDeniedHandler {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AccessDeniedHandler.class);

    private final ObjectMapper objectMapper;

    public AccessDeniedHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException) throws IOException, ServletException {

        if (response.isCommitted()) {
            LOGGER.info("not sending back access denied response because response is already committed");
            return;
        }

        boolean acceptsJson = Optional.ofNullable(request.getHeader(HttpHeaders.ACCEPT))
                .map(MediaType::parseMediaTypes)
                .map(mts -> mts.stream().anyMatch(MediaType.APPLICATION_JSON::equalsTypeAndSubtype))
                .orElse(false);

        if (acceptsJson) {
            LOGGER.debug("responding access denied as json-rpc");
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON.toString());
            objectMapper.writeValue(
                    response.getWriter(),
                    Map.of(
                            "error",
                            Map.of(
                                    "code", Constants.ERROR_CODE_AUTHORIZATIONFAILURE,
                                    "message", "authorizationfailure"
                            )
                    ));
        } else {
            LOGGER.debug("responding access denied as not json-rpc");
            request.setAttribute(WebAttributes.ACCESS_DENIED_403, accessDeniedException);
            response.setStatus(HttpStatus.FORBIDDEN.value());
            request.getRequestDispatcher("__error").forward(request, response);
        }
    }

}
