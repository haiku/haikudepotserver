/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersionInteraction;

public class PkgVersionInteraction extends _PkgVersionInteraction {

    private static final long serialVersionUID = 1L;

    @Override
    public void validateForInsert(ValidationResult validationResult) {
        if (null == getViewCounter()) {
            setViewCounter(0L);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if (getViewCounter() < 0) {
            validationResult.addFailure(new BeanValidationFailure(this, VIEW_COUNTER.getName(), "min"));
        }

    }

    public void incrementViewCounter() {
        if (null == getViewCounter()) {
            setViewCounter(1L);
        } else {
            setViewCounter(getViewCounter() + 1);
        }
    }

}
