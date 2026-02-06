/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._JobAssignment;

import java.io.Serial;
import java.util.regex.Pattern;

public class JobAssignment extends _JobAssignment {

    @Serial
    private static final long serialVersionUID = 1L;

    private final static Pattern PATTERN_CODE = Pattern.compile("^[a-z0-9-]{36}");

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (null != getCode()) {
            if (!PATTERN_CODE.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, CODE.getName(), "malformed"));
            }
        }

    }
}
