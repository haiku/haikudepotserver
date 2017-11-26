/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersionUrl;
import org.haiku.haikudepotserver.support.URLHelper;

import java.net.MalformedURLException;
import java.net.URL;

public class PkgVersionUrl extends _PkgVersionUrl {

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getUrl()) {
            if (!URLHelper.isValidInfo(getUrl())) {
                validationResult.addFailure(new BeanValidationFailure(this, URL.getName(), "malformed"));
            }
        }

    }

}
