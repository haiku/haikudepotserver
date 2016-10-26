/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects.support;

import org.apache.cayenne.CayenneDataObject;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;

import java.util.regex.Pattern;

/**
 * <p>This is the superclass of the Cayanne Data Objects in this project.  This contains some common handling for
 * all data objects.</p>
 */

public abstract class AbstractDataObject extends CayenneDataObject {

    public final static Pattern CODE_PATTERN = Pattern.compile("^[a-z0-9]{2,16}$");

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        // If we implement the Coded interface then we can validate the code on this
        // object to check it is valid.

        if (Coded.class.isAssignableFrom(this.getClass())) {

            Coded coded = (Coded) this;

            if (null != coded.getCode()) {
                if(!CODE_PATTERN.matcher(coded.getCode()).matches()) {
                    validationResult.addFailure(new BeanValidationFailure(this, "code", "malformed"));
                }
            }
        }

    }

}
