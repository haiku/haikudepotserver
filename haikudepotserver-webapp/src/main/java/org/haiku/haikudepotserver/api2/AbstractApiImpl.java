/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.common.base.Throwables;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.SimpleValidationFailure;
import org.apache.cayenne.validation.ValidationFailure;
import org.haiku.haikudepotserver.api1.support.Constants;
import org.haiku.haikudepotserver.api2.model.Error;
import org.haiku.haikudepotserver.api2.model.ErrorData;
import org.haiku.haikudepotserver.support.exception.AuthorizationRuleConflictException;
import org.haiku.haikudepotserver.support.exception.BadPkgIconException;
import org.haiku.haikudepotserver.support.exception.CaptchaBadResponseException;
import org.haiku.haikudepotserver.support.exception.InvalidUserUsageConditionsException;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.support.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.stream.Collectors;

public abstract class AbstractApiImpl {

    protected static Logger LOGGER = LoggerFactory.getLogger(AbstractApiImpl.class);

    @ExceptionHandler
    @ResponseBody
    public ResponseEntity<Envelope> handleException(Throwable t) {
        return ResponseEntity.ok(new Envelope(resolveError((Throwables.getRootCause(t)))));
    }

    public Error resolveError(Throwable t) {

        if (t instanceof InvalidUserUsageConditionsException) {
            return new Error()
                    .code(Constants.ERROR_CODE_INVALID_USER_USAGE_CONDITIONS)
                    .message("invaliduserusageconditions");
        }

        if (AuthorizationRuleConflictException.class.isAssignableFrom(t.getClass())) {
            return new Error()
                    .code(Constants.ERROR_CODE_AUTHORIZATIONRULECONFLICT)
                    .message("authorizationruleconflict");
        }

        // output for authorization failure.

        if (AccessDeniedException.class.isAssignableFrom(t.getClass())) {
            return new Error()
                    .code(Constants.ERROR_CODE_AUTHORIZATIONFAILURE)
                    .message("authorizationfailure");
        }

        // special output for a bad captcha

        if (CaptchaBadResponseException.class.isAssignableFrom(t.getClass())) {
            return new Error()
                    .code(Constants.ERROR_CODE_CAPTCHABADRESPONSE)
                    .message("captchabadresponse");
        }

        if (BadPkgIconException.class.isAssignableFrom(t.getClass())) {
            BadPkgIconException badPkgIconException = (BadPkgIconException) t;
            Error error = new Error()
                    .code(Constants.ERROR_CODE_CAPTCHABADRESPONSE)
                    .message("captchabadresponse")
                    .addDataItem(new ErrorData()
                            .key("mediaTypeCode")
                            .value(badPkgIconException.getMediaTypeCode()));

            if(null != badPkgIconException.getSize()) {
                error.addDataItem(new ErrorData()
                        .key("size")
                        .value(Integer.toString(badPkgIconException.getSize())));
            }

            return error;
        }

        // special output for the object not found exceptions

        if(ObjectNotFoundException.class.isAssignableFrom(t.getClass())) {
            ObjectNotFoundException objectNotFoundException = (ObjectNotFoundException) t;
            Error error = new Error()
                    .code(Constants.ERROR_CODE_OBJECTNOTFOUND)
                    .message("objectnotfound")
                    .addDataItem(new ErrorData()
                            .key("entityName")
                            .value(objectNotFoundException.getEntityName()));

            if (null != objectNotFoundException.getIdentifier()) {
                error.addDataItem(new ErrorData()
                    .key("identifier")
                    .value(objectNotFoundException.getIdentifier().toString()));
            }

            return error;
        }

        // special output for the validation exceptions

        if(ValidationException.class.isAssignableFrom(t.getClass())) {
            ValidationException validationException = (ValidationException) t;
            return new Error()
                    .code(Constants.ERROR_CODE_VALIDATION)
                    .message("validationerror")
                            .data(validationException.getValidationFailures()
                                    .stream()
                                    .map(ve -> new ErrorData()
                                            .key(ve.getProperty())
                                            .value(ve.getMessage()))
                                    .collect(Collectors.toUnmodifiableList()));
        }

        // special output for cayenne validation exceptions

        if(org.apache.cayenne.validation.ValidationException.class.isAssignableFrom(t.getClass())) {
            org.apache.cayenne.validation.ValidationException validationException = (org.apache.cayenne.validation.ValidationException) t;
            return new Error()
                    .code(Constants.ERROR_CODE_VALIDATION)
                    .message("validationerror")
                    .data(validationException.getValidationResult().getFailures()
                            .stream()
                            .map(AbstractApiImpl::mapToErrorData)
                            .collect(Collectors.toUnmodifiableList()));
        }

        LOGGER.error("unhandled exception", t);

        return new Error()
                .code(Constants.ERROR_CODE_NOT_HANDLED)
                .message("nothandled");
    }

    private static ErrorData mapToErrorData(ValidationFailure vf) {
        if (BeanValidationFailure.class.isAssignableFrom(vf.getClass())) {
            BeanValidationFailure beanValidationFailure = (BeanValidationFailure) vf;
            Object err = beanValidationFailure.getError();
            return new ErrorData()
                    .key(beanValidationFailure.getProperty())
                    .value(null != err ? err.toString() : "");
        }

        if (SimpleValidationFailure.class.isAssignableFrom(vf.getClass())) {
            SimpleValidationFailure simpleValidationFailure = (SimpleValidationFailure) vf;
            return new ErrorData()
                    .key("")
                    .value(simpleValidationFailure.getDescription());
        }

        throw new IllegalStateException("unable to establish data portion of validation exception owing to unknown "
                + "cayenne validation failure; " + vf.getClass().getSimpleName());
    }

    public static class Envelope {

        @JsonProperty("error")
        private Error error;

        public Envelope(Error error) {
            this.error = error;
        }

        public Error getError() {
            return error;
        }
    }

}
