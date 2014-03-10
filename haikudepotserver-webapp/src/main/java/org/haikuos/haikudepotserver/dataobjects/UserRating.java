/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._UserRating;

public class UserRating extends _UserRating {

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null!=getRating()) {
            if(getRating() < 0) {
                validationResult.addFailure(new BeanValidationFailure(this,RATING_PROPERTY,"min"));
            }

            if(getRating() > 5) {
                validationResult.addFailure(new BeanValidationFailure(this,RATING_PROPERTY,"max"));
            }
        }
    }

}
