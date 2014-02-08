/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.SimpleValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._Pkg;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

import java.util.Date;
import java.util.List;
import java.util.regex.Pattern;

public class Pkg extends _Pkg implements CreateAndModifyTimestamped {

    public static Pattern NAME_PATTERN = Pattern.compile("^[^\\s/=!<>-]+$");

    public static Optional<Pkg> getByName(ObjectContext context, String name) {
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<Pkg>) context.performQuery(new SelectQuery(
                        Pkg.class,
                        ExpressionFactory.matchExp(Pkg.NAME_PROPERTY, name))),
                null));
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getName()) {
            if(!NAME_PATTERN.matcher(getName()).matches()) {
                validationResult.addFailure(new SimpleValidationFailure(this,NAME_PATTERN + ".malformed"));
            }
        }

    }

    public Optional<PkgIconImage> getPkgIconImage(final MediaType mediaType, final int size) {
        Preconditions.checkNotNull(mediaType);
        Preconditions.checkState(16==size||32==size);

        Optional<PkgIcon> pkgIconOptional = getPkgIcon(mediaType,size);

        if(pkgIconOptional.isPresent()) {
            Optional<PkgIconImage> pkgIconImageOptional = pkgIconOptional.get().getPkgIconImage();

            if(pkgIconImageOptional.isPresent()) {
                return pkgIconImageOptional;
            }
            else {
                throw new IllegalStateException("a pkg icon does not have an image associated with it.");
            }
        }

        return Optional.absent();
    }

    public Optional<PkgIcon> getPkgIcon(final MediaType mediaType, final int size) {
        Preconditions.checkNotNull(mediaType);
        Preconditions.checkState(16==size||32==size);

        for(PkgIcon pkgIcon : getPkgIcons()) {
            if(pkgIcon.getMediaType().equals(mediaType) && pkgIcon.getSize()==size) {
                return Optional.of(pkgIcon);
            }
        }

        return Optional.absent();
    }

    public void setModifyTimestamp() {
        setModifyTimestamp(new java.util.Date());
    }

    /**
     * <p>The regular {$link #getModifyTimestamp} is at millisecond accuracy.  When dealing with second-accuracy
     * dates, this can cause anomalies.  For this reason, this method will provide the modify timestamp at
     * second accuracy.</p>
     */

    public Date getModifyTimestampSecondAccuracy() {
        return new java.util.Date((getModifyTimestamp().getTime() / 1000) * 1000);
    }

}