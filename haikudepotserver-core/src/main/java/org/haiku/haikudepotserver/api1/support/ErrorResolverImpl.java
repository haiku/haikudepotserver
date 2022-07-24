/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.googlecode.jsonrpc4j.DefaultErrorResolver;
import com.googlecode.jsonrpc4j.ErrorResolver;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.SimpleValidationFailure;
import org.haiku.haikudepotserver.support.exception.AuthorizationRuleConflictException;
import org.haiku.haikudepotserver.support.exception.BadPkgIconException;
import org.haiku.haikudepotserver.support.exception.CaptchaBadResponseException;
import org.haiku.haikudepotserver.support.exception.InvalidUserUsageConditionsException;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.support.exception.ValidationException;
import org.springframework.security.access.AccessDeniedException;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * <p>This class is able to take exceptions and throwables and turn them into valid JSON-RPC errors that can be
 * returned to the client with specific codes and with specific data.</p>
 */

@Deprecated
public class ErrorResolverImpl implements ErrorResolver {

    @Override
    public JsonError resolveError(Throwable t, Method method, List<JsonNode> arguments) {

        if (t instanceof InvalidUserUsageConditionsException) {
            return new JsonError(
                    Constants.ERROR_CODE_INVALID_USER_USAGE_CONDITIONS,
                    "invaliduserusageconditions",
                    null);
        }

        if (AuthorizationRuleConflictException.class.isAssignableFrom(t.getClass())) {
            return new JsonError(
                    Constants.ERROR_CODE_AUTHORIZATIONRULECONFLICT,
                    "authorizationruleconflict",
                    null);
        }

        // output for authorization failure.

        if (AccessDeniedException.class.isAssignableFrom(t.getClass())) {
            return new JsonError(
                    Constants.ERROR_CODE_AUTHORIZATIONFAILURE,
                    "authorizationfailure",
                    null);
        }

        // special output for a bad captcha

        if (CaptchaBadResponseException.class.isAssignableFrom(t.getClass())) {
            return new JsonError(
                    Constants.ERROR_CODE_CAPTCHABADRESPONSE,
                    "captchabadresponse",
                    null);
        }

        if (BadPkgIconException.class.isAssignableFrom(t.getClass())) {
            BadPkgIconException badPkgIconException = (BadPkgIconException) t;

            Map<String,Object> errorData = Maps.newHashMap();
            errorData.put("mediaTypeCode", badPkgIconException.getMediaTypeCode());

            if(null != badPkgIconException.getSize()) {
                errorData.put("size", badPkgIconException.getSize());
            }

            return new JsonError(
                    Constants.ERROR_CODE_BADPKGICON,
                    "badpkgicon",
                    errorData);
        }

        // special output for the object not found exceptions

        if(ObjectNotFoundException.class.isAssignableFrom(t.getClass())) {
            ObjectNotFoundException objectNotFoundException = (ObjectNotFoundException) t;

            return new JsonError(
                    Constants.ERROR_CODE_OBJECTNOTFOUND,
                    "objectnotfound",
                    ImmutableMap.of(
                            "entityName", objectNotFoundException.getEntityName(),
                            "identifier", objectNotFoundException.getIdentifier()
                    )
            );
        }

        // special output for the validation exceptions

        if(ValidationException.class.isAssignableFrom(t.getClass())) {
            ValidationException validationException = (ValidationException) t;

            return new JsonError(
                    Constants.ERROR_CODE_VALIDATION,
                    "validationerror",
                    Collections.singletonMap(
                            "validationfailures",
                            validationException.getValidationFailures()
                                    .stream()
                                    .map(vf -> ImmutableMap.of(
                                            "property",vf.getProperty(),
                                            "message",vf.getMessage()
                                    ))
                                    .collect(Collectors.toList())
                    )
            );
        }

        // special output for cayenne validation exceptions

        if(org.apache.cayenne.validation.ValidationException.class.isAssignableFrom(t.getClass())) {
            org.apache.cayenne.validation.ValidationException validationException = (org.apache.cayenne.validation.ValidationException) t;

            return new JsonError(
                    Constants.ERROR_CODE_VALIDATION,
                    "validationerror",
                    Collections.singletonMap(
                            "validationfailures",
                            validationException.getValidationResult().getFailures()
                                    .stream()
                                    .map(f -> {
                                        if (BeanValidationFailure.class.isAssignableFrom(f.getClass())) {
                                            BeanValidationFailure beanValidationFailure = (BeanValidationFailure) f;
                                            Object err = beanValidationFailure.getError();
                                            return ImmutableMap.of(
                                                    "property", beanValidationFailure.getProperty(),
                                                    "message", null != err ? err.toString() : "");
                                        }

                                        if (SimpleValidationFailure.class.isAssignableFrom(f.getClass())) {
                                            SimpleValidationFailure simpleValidationFailure = (SimpleValidationFailure) f;
                                            return ImmutableMap.of(
                                                    "property", "",
                                                    "message", simpleValidationFailure.getDescription());
                                        }

                                        throw new IllegalStateException("unable to establish data portion of validation exception owing to unknown cayenne validation failure; " + f.getClass().getSimpleName());
                                    })
                                    .collect(Collectors.toList())
                    )
            );
        }

        return DefaultErrorResolver.INSTANCE.resolveError(t,method,arguments);
    }
}
