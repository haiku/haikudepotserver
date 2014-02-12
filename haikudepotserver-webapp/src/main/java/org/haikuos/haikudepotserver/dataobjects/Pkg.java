/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._Pkg;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

import java.util.Collections;
import java.util.Comparator;
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
                validationResult.addFailure(new BeanValidationFailure(this,NAME_PROPERTY,"malformed"));
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

    public List<PkgScreenshot> getSortedPkgScreenshots() {
        List<PkgScreenshot> screenshots = Lists.newArrayList(getPkgScreenshots());
        Collections.sort(screenshots);
        return screenshots;
    }

    public Optional<Integer> getHighestPkgScreenshotOrdering() {
        List<PkgScreenshot> screenshots = getSortedPkgScreenshots();

        if(screenshots.isEmpty()) {
            return Optional.absent();
        }

        return Optional.of(screenshots.get(screenshots.size()-1).getOrdering());
    }

    /**
     * <p>This method will re-order the screenshots according to the set of codes present in the supplied list.
     * If the same code appears twice in the list, an {@link java.lang.IllegalArgumentException} will be
     * thrown.  Any screenshots that are not mentioned in the list will be indeterminately ordered at the end
     * of the list.</p>
     */

    public void reorderPkgScreenshots(final List<String> codes) {
        Preconditions.checkNotNull(codes);

        // first check that there are no duplicates.
        if(ImmutableSet.copyOf(codes).size() != codes.size()) {
            throw new IllegalArgumentException("the codes supplied contain duplicates which would interfere with the ordering");
        }

        List<PkgScreenshot> screenshots = Lists.newArrayList(getPkgScreenshots());
        Collections.sort(
                screenshots,
                new Comparator<PkgScreenshot>() {
                    @Override
                    public int compare(PkgScreenshot o1, PkgScreenshot o2) {
                        int o1i = codes.indexOf(o1.getCode());
                        int o2i = codes.indexOf(o2.getCode());

                        if(-1==o1i && -1==o2i) {
                            return o1.getCode().compareTo(o2.getCode());
                        }

                        if(-1==o1i) {
                            o1i = Integer.MAX_VALUE;
                        }

                        if(-1==o2i) {
                            o2i = Integer.MAX_VALUE;
                        }

                        return Integer.compare(o1i,o2i);
                    }
                }
        );

        for(int i=0;i<screenshots.size();i++) {
            PkgScreenshot pkgScreenshot = screenshots.get(i);
            pkgScreenshot.setOrdering(i+1);
        }
    }

}