/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class PkgScreenshot extends _PkgScreenshot implements Comparable<PkgScreenshot>, CreateAndModifyTimestamped {

    public static PkgScreenshot getByCode(ObjectContext context, String code) {
        return tryGetByCode(context, code)
                .orElseThrow(() -> new IllegalStateException("unable to find the screenshot with code [" + code + "]"));
    }

    public static Optional<PkgScreenshot> tryGetByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be supplied");
        List<PkgScreenshot> pkgScreenshots = (List<PkgScreenshot>) context.performQuery(new SelectQuery(
                PkgScreenshot.class,
                ExpressionFactory.matchExp(PkgScreenshot.CODE_PROPERTY, code)));
        return pkgScreenshots.stream().collect(SingleCollector.optional());
    }

    public PkgScreenshotImage getPkgScreenshotImage() {
        return tryGetPkgScreenshotImage()
                .orElseThrow(() -> new IllegalStateException("unable to find the pkg screenshot's image"));
    }

    /**
     * <p>As there should be only one of these, if there are two then this method will throw an
     * {@link IllegalStateException}.</p>
     */

    public Optional<PkgScreenshotImage> tryGetPkgScreenshotImage() {
        List<PkgScreenshotImage> images = getPkgScreenshotImages();

        switch(images.size()) {
            case 0: return Optional.empty();
            case 1: return Optional.of(images.get(0));
            default:
                throw new IllegalStateException("more than one pkg icon image found on an icon image");
        }
    }

    @Override
    public int compareTo(PkgScreenshot o) {
        return getOrdering().compareTo(o.getOrdering());
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(getHeight() <= 0) {
            validationResult.addFailure(new BeanValidationFailure(this,HEIGHT_PROPERTY,"range"));
        }

        if(getWidth() <= 0) {
            validationResult.addFailure(new BeanValidationFailure(this,WIDTH_PROPERTY,"range"));
        }

        if(getLength() <= 0) {
            validationResult.addFailure(new BeanValidationFailure(this,LENGTH_PROPERTY,"range"));
        }
    }

}
