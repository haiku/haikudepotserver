/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.exception;

/**
 * <p>This object models a validation failure in the system.  The property indicates what element of the object in
 * question has failed validation checks and the message indicates what was wrong with that element.</p>
 */

public class ValidationFailure {

    private String property;
    private String message;

    public ValidationFailure(String property, String message) {
        super();

        if(null==property || 0==property.length()) {
            throw new IllegalStateException("the property is required for a validation failure");
        }

        if(null==message || 0==message.length()) {
            throw new IllegalStateException("the message is required for a validation failure");
        }

        this.property = property;
        this.message = message;
    }

    public String getProperty() {
        return property;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return String.format("%s; %s",getProperty(),getMessage());
    }

}
