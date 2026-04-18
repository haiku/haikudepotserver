/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.auto._JobTag;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.io.Serial;
import java.util.regex.Pattern;

/**
 * <p>This entity carries a key-value pair that can be associated with a {@link Job}. This
 * is typically from the {@link JobSpecification#getTags()}.</p>
 */

public class JobTag extends _JobTag {

    @Serial
    private static final long serialVersionUID = 1L;

    private final static Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9_]+$");

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (null != getCode()) {
            if (!CODE_PATTERN.matcher(getCode()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, CODE.getName(), "malformed"));
            }
        }

        if (null != getValue()) {
            if (StringUtils.isBlank(getValue())) {
                validationResult.addFailure(new BeanValidationFailure(this, VALUE.getName(), "empty"));
            }
        }

    }

}
