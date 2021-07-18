/*
 * Copyright 2021, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.SimpleValidationFailure;
import org.apache.commons.lang3.tuple.Pair;
import org.haiku.haikudepotserver.api1.model.authorization.AuthorizationRuleConflictException;
import org.haiku.haikudepotserver.api1.support.*;
import org.haiku.haikudepotserver.api2.model.GenericErrorResponseEnvelope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class AbstractApiImpl {

    private final Logger LOGGER = LoggerFactory.getLogger(this.getClass());

    /**
     * <p>This method should map any exceptions into a JSON structure that is
     * documented in <code>error.yaml</code> for the API version 2.</p>
     */

    @ResponseStatus(HttpStatus.OK)
    @ExceptionHandler(Throwable.class)
    public @ResponseBody
    GenericErrorResponseEnvelope handleException(Throwable t) {

        if (t instanceof InvalidUserUsageConditionsException) {
            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_INVALID_USER_USAGE_CONDITIONS,
                    "invaliduserusageconditions");
        }

        if (AuthorizationRuleConflictException.class.isAssignableFrom(t.getClass())) {
            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_AUTHORIZATIONRULECONFLICT,
                    "authorizationruleconflict");
        }

        // output for authorization failure.

        if (AccessDeniedException.class.isAssignableFrom(t.getClass())) {
            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_AUTHORIZATIONFAILURE,
                    "authorizationfailure");
        }

        // special output for a bad captcha

        if (CaptchaBadResponseException.class.isAssignableFrom(t.getClass())) {
            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_CAPTCHABADRESPONSE,
                    "captchabadresponse");
        }

        if (BadPkgIconException.class.isAssignableFrom(t.getClass())) {
            BadPkgIconException badPkgIconException = (BadPkgIconException) t;

            Map<String, String> errorData = Maps.newHashMap();
            errorData.put("mediaTypeCode", badPkgIconException.getMediaTypeCode());

            if(null != badPkgIconException.getSize()) {
                errorData.put("size", badPkgIconException.getSize().toString());
            }

            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_BADPKGICON,
                    "badpkgicon",
                    errorData);
        }

        // special output for the object not found exceptions

        if(ObjectNotFoundException.class.isAssignableFrom(t.getClass())) {
            ObjectNotFoundException objectNotFoundException = (ObjectNotFoundException) t;

            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_OBJECTNOTFOUND,
                    "objectnotfound",
                    ImmutableMap.of(
                            "entityName",
                            objectNotFoundException.getEntityName(),
                            "identifier",
                            Optional.ofNullable(objectNotFoundException.getIdentifier())
                                .map(Object::toString).orElse("")
                    )
            );
        }

        // special output for the validation exceptions

        if(ValidationException.class.isAssignableFrom(t.getClass())) {
            ValidationException validationException = (ValidationException) t;

            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_VALIDATION,
                    "validationerror",
                    validationException.getValidationFailures()
                            .stream()
                            .collect(Collectors.toUnmodifiableMap(
                                    ValidationFailure::getProperty,
                                    ValidationFailure::getMessage)));
        }

        // special output for cayenne validation exceptions

        if(org.apache.cayenne.validation.ValidationException.class.isAssignableFrom(t.getClass())) {
            org.apache.cayenne.validation.ValidationException validationException = (org.apache.cayenne.validation.ValidationException) t;

            return new GenericErrorResponseEnvelope(
                    Constants.ERROR_CODE_VALIDATION,
                    "validationerror",
                    Optional.ofNullable(validationException.getValidationResult())
                            .map(vr -> vr.getFailures()
                                    .stream()
                                    .map(this::toErrorData)
                                    .collect(Collectors.toUnmodifiableMap(
                                            Pair::getKey,
                                            Pair::getValue)))
                            .orElse(null));
        }

        LOGGER.info("unhandled error arisen in api2", t);

        return new GenericErrorResponseEnvelope(
                Constants.ERROR_CODE_NOT_HANDLED,
                "unhandled error");
    }

    private Pair<String, String> toErrorData(org.apache.cayenne.validation.ValidationFailure f) {
        if (BeanValidationFailure.class.isAssignableFrom(f.getClass())) {
            BeanValidationFailure beanValidationFailure = (BeanValidationFailure) f;
            Object err = beanValidationFailure.getError();
            return Pair.of(beanValidationFailure.getProperty(), Optional.ofNullable(err).map(Object::toString).orElse(""));
        }

        if (SimpleValidationFailure.class.isAssignableFrom(f.getClass())) {
            SimpleValidationFailure simpleValidationFailure = (SimpleValidationFailure) f;
            return Pair.of(
                    Optional.ofNullable(simpleValidationFailure.getSource())
                            .map(Object::toString)
                            .orElse(""),
                    Optional.ofNullable(simpleValidationFailure.getError())
                            .map(Object::toString)
                            .orElse(""));
        }

        throw new IllegalStateException("unable to establish data portion of validation exception owing to unknown cayenne validation failure; " + f.getClass().getSimpleName());
    }

}
