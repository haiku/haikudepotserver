/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.googlecode.jsonrpc4j.DefaultErrorResolver;
import com.googlecode.jsonrpc4j.ErrorResolver;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.SimpleValidationFailure;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * <p>This class is able to take exceptions and throwables and turn them into valid JSON-RPC errors that can be
 * returned to the client with specific codes and with specific data.</p>
 */

public class ErrorResolverImpl implements ErrorResolver {

    @Override
    public JsonError resolveError(Throwable t, Method method, List<JsonNode> arguments) {

        // output for authorization failure.

        if(AuthorizationFailureException.class.isAssignableFrom(t.getClass())) {
            return new JsonError(
                    Constants.ERROR_CODE_AUTHORIZATIONFAILURE,
                    "authorizationfailure",
                    null);
        }

        // special output for a bad captcha

        if(CaptchaBadResponseException.class.isAssignableFrom(t.getClass())) {
            return new JsonError(
                    Constants.ERROR_CODE_CAPTCHABADRESPONSE,
                    "captchabadresponse",
                    null);
        }

        // special output for the object not found exceptions

        if(ObjectNotFoundException.class.isAssignableFrom(t.getClass())) {
            ObjectNotFoundException objectNotFoundException = (ObjectNotFoundException) t;

            return new JsonError(
                    Constants.ERROR_CODE_OBJECTNOTFOUND,
                    "objectnotfound",
                    ImmutableMap.of(
                            "entityname", objectNotFoundException.getEntityName(),
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
                    ImmutableMap.of(
                            "validationfailures",
                            Lists.transform(
                                    validationException.getValidationFailures(),
                                    new Function<ValidationFailure, Object>() {
                                        @Override
                                        public Map<String,String> apply(org.haikuos.haikudepotserver.api1.support.ValidationFailure input) {
                                            return ImmutableMap.of(
                                                    "property",input.getProperty(),
                                                    "message",input.getMessage()
                                            );
                                        }
                                    }
                            )
                    )
            );
        }

        // special output for cayenne validation exceptions

        if(org.apache.cayenne.validation.ValidationException.class.isAssignableFrom(t.getClass())) {
            org.apache.cayenne.validation.ValidationException validationException = (org.apache.cayenne.validation.ValidationException) t;

            return new JsonError(
                    Constants.ERROR_CODE_VALIDATION,
                    "validationerror",
                    ImmutableMap.of(
                            "validationfailures",
                            Lists.transform(
                                    validationException.getValidationResult().getFailures(),
                                    new Function<org.apache.cayenne.validation.ValidationFailure, Object>() {
                                        @Override
                                        public Map<String,String> apply(org.apache.cayenne.validation.ValidationFailure input) {
                                            if(BeanValidationFailure.class.isAssignableFrom(input.getClass())) {
                                                BeanValidationFailure beanValidationFailure = (BeanValidationFailure) input;
                                                return ImmutableMap.of(
                                                        "property", beanValidationFailure.getProperty(),
                                                        "message", beanValidationFailure.toString());
                                            }

                                            if(SimpleValidationFailure.class.isAssignableFrom(input.getClass())) {
                                               SimpleValidationFailure simpleValidationFailure = (SimpleValidationFailure) input;
                                                return ImmutableMap.of(
                                                        "property", "",
                                                        "message", simpleValidationFailure.getDescription());
                                            }

                                            throw new IllegalStateException("unable to establish data portion of validation exception owing to unknown cayenne validation failure; "+input.getClass().getSimpleName());
                                        }
                                    }
                            )
                    )
            );
        }

        return DefaultErrorResolver.INSTANCE.resolveError(t,method,arguments);
    }
}
