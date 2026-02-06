/*
 * Copyright 2013-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.exception;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

/**
 * <p>This object models a validation failure in the system.  The property indicates what element of the object in
 * question has failed validation checks and the message indicates what was wrong with that element.</p>
 */

public class ValidationFailure {

    private final String property;
    private final String message;

    public ValidationFailure(String property, String message) {
        Preconditions.checkArgument(StringUtils.isNotBlank(property), "the property is required");
        Preconditions.checkArgument(StringUtils.isNotBlank(message), "the message is required");

        super();

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
