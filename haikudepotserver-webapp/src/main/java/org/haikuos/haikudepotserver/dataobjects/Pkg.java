/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._Pkg;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.support.SingleCollector;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class Pkg extends _Pkg implements CreateAndModifyTimestamped {

    public static Pattern NAME_PATTERN = Pattern.compile("^[^\\s/=!<>-]+$");

    public static Optional<Pkg> getByName(ObjectContext context, String name) {
        Preconditions.checkArgument(null!=context, "a context must be provided to lookup a package");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "a name must be provided to get a package");

        SelectQuery query = new SelectQuery(
                Pkg.class,
                ExpressionFactory.matchExp(Pkg.NAME_PROPERTY, name));

        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.setCacheGroups(HaikuDepot.CacheGroup.PKG.name());

        return ((List<Pkg>) context.performQuery(query)).stream().collect(SingleCollector.optional());
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if(null==getActive()) {
            setActive(true);
        }

        if(null==getDerivedRatingSampleSize()) {
            setDerivedRatingSampleSize(0);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getName()) {
            if(!NAME_PATTERN.matcher(getName()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,NAME_PROPERTY,"malformed"));
            }
        }

        if(null != getDerivedRating()) {
            if(getDerivedRating() < 0f) {
                validationResult.addFailure(new BeanValidationFailure(this,DERIVED_RATING_PROPERTY,"min"));
            }

            if(getDerivedRating() > 5f) {
                validationResult.addFailure(new BeanValidationFailure(this,DERIVED_RATING_PROPERTY,"max"));
            }
        }

        if(getDerivedRatingSampleSize() < 0) {
            validationResult.addFailure(new BeanValidationFailure(this, DERIVED_RATING_SAMPLE_SIZE_PROPERTY, "min"));
        }

    }

    public Optional<PkgPkgCategory> getPkgPkgCategory(String pkgCategoryCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(pkgCategoryCode));

        for(PkgPkgCategory pkgPkgCategory : getPkgPkgCategories()) {
            if(pkgPkgCategory.getPkgCategory().getCode().equals(pkgCategoryCode)) {
                return Optional.of(pkgPkgCategory);
            }
        }

        return Optional.empty();
    }

    public Optional<PkgIcon> getPkgIcon(final MediaType mediaType, final Integer size) {
        Preconditions.checkNotNull(mediaType);

        List<PkgIcon> icons = getPkgIcons(mediaType,size);

        switch(icons.size()) {
            case 0:
                return Optional.empty();

            case 1:
                return Optional.of(icons.get(0));

            default:
                throw new IllegalStateException("more than one pkg icon of media type "+mediaType.getCode()+" of size "+size+" on pkg "+getName());
        }
    }

    private List<PkgIcon> getPkgIcons(final MediaType mediaType, final Integer size) {
        Preconditions.checkNotNull(mediaType);
        return getPkgIcons().stream().filter(pi -> {
            return pi.getMediaType().equals(mediaType)
                    && (null == size) == (null == pi.getSize())
                    && ((null == size) || size.equals(pi.getSize()));
        }).collect(Collectors.toList());
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
        List<PkgScreenshot> screenshots = new ArrayList<>(getPkgScreenshots());
        Collections.sort(screenshots);
        return screenshots;
    }

    public Optional<Integer> getHighestPkgScreenshotOrdering() {
        List<PkgScreenshot> screenshots = getSortedPkgScreenshots();

        if(screenshots.isEmpty()) {
            return Optional.empty();
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
        if(codes.stream().collect(Collectors.toSet()).size() != codes.size()) {
            throw new IllegalArgumentException("the codes supplied contain duplicates which would interfere with the ordering");
        }

        List<PkgScreenshot> screenshots = new ArrayList<>(getPkgScreenshots());
        Collections.sort(
                screenshots,
                (o1, o2) -> {
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
        );

        for(int i=0;i<screenshots.size();i++) {
            PkgScreenshot pkgScreenshot = screenshots.get(i);
            pkgScreenshot.setOrdering(i+1);
        }
    }

    public UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        return builder.pathSegment(getName());
    }

    @Override
    public String toString() {
        return "pkg;"+getName();
    }

}