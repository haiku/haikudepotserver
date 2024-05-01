/*
 * Copyright 2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySourceExtraIdentifier;

import java.io.Serial;

public class RepositorySourceExtraIdentifier extends _RepositorySourceExtraIdentifier {

    @Serial
    private static final long serialVersionUID = 1L;

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getIdentifier()) {
            if (StringUtils.isBlank(getIdentifier())) {
                validationResult.addFailure(new BeanValidationFailure(this, IDENTIFIER.getName(), "malformed"));
            }
        }

    }

}
