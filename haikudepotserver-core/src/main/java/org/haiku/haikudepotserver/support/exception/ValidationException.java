/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.exception;

import java.util.Collections;
import java.util.List;

/**
 * <p>This exception is thrown in the situation where a validation exception is thrown outside of the Cayenne
 * infrastructure.</p>
 */

public class ValidationException extends Error {

    protected List<ValidationFailure> validationFailures;

    public ValidationException(ValidationFailure validationFailure) {
        super();

        if(null==validationFailure) {
            throw new IllegalStateException();
        }

        this.validationFailures = Collections.singletonList(validationFailure);
    }

    public ValidationException(List<ValidationFailure> validationFailures) {
        super();

        if(null==validationFailures) {
            throw new IllegalStateException();
        }

        this.validationFailures = validationFailures;
    }

    public List<ValidationFailure> getValidationFailures() {
        return validationFailures;
    }

    @Override
    public String getMessage() {
        switch(validationFailures.size()) {
            case 0:
                return "unknown validation failure";
            case 1:
                return "validation failure - " + validationFailures.get(0).toString();
            default:
                return String.format("%d validation failures", validationFailures.size());
        }
    }

}
