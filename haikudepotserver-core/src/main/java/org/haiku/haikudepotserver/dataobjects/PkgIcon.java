/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PkgIcon;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.Optional;

public class PkgIcon extends _PkgIcon {

    public final static String VALIDATION_REQUIREDFORBITMAP = "requiredforbitmap";
    public final static String VALIDATION_NOTALLOWEDFORVECTOR = "notallowedforvector";

    /**
     * <p>As there should be only one of these, if there are two then this method will throw an
     * {@link IllegalStateException}.</p>
     */

    public Optional<PkgIconImage> getPkgIconImage() {
        return getPkgIconImages().stream().collect(SingleCollector.optional());
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        // vector artwork should not be stored with a size because it makes no sense.

        if(com.google.common.net.MediaType.PNG.toString().equals(getMediaType().getCode())) {
            if(null==getSize()) {
                validationResult.addFailure(new BeanValidationFailure(this,SIZE_PROPERTY,VALIDATION_REQUIREDFORBITMAP));
            }
        }

        if(MediaType.MEDIATYPE_HAIKUVECTORICONFILE.equals(getMediaType().getCode())) {
            if(null!=getSize()) {
                validationResult.addFailure(new BeanValidationFailure(this,SIZE_PROPERTY,VALIDATION_NOTALLOWEDFORVECTOR));
            }
        }

    }

    /**
     * <p>This method will return a string which represents a leafname for the icon.</p>
     */

    public String deriveFilename() {

        if(com.google.common.net.MediaType.PNG.toString().equals(getMediaType().getCode())) {
            return Integer.toString(getSize()) + ".png";
        }

        if(MediaType.MEDIATYPE_HAIKUVECTORICONFILE.equals(getMediaType().getCode())) {
            return "icon.hvif";
        }

        throw new IllegalStateException("unsupported media type; " + getMediaType().getCode());
    }

}
