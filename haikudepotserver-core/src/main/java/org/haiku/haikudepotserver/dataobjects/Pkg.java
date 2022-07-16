/*
 * Copyright 2013-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.auto._Pkg;
import org.haiku.haikudepotserver.dataobjects.support.MutableCreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.regex.Pattern;

public class Pkg extends _Pkg implements MutableCreateAndModifyTimestamped {

    public static final String PATTERN_STRING_NAME_CHAR = "[^\\s/=!<>-]";

    public static final Pattern PATTERN_NAME = Pattern.compile("^" + PATTERN_STRING_NAME_CHAR + "+$");

    public static Pkg getByName(ObjectContext context, String name) {
        return tryGetByName(context, name)
                .orElseThrow(() -> new IllegalStateException("unable to find pkg for name [" + name + "]"));
    }

    public static Optional<Pkg> tryGetByName(ObjectContext context, String name) {
        Preconditions.checkArgument(null!=context, "a context must be provided to lookup a package");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(name), "a name must be provided to get a package");

        return Optional.ofNullable(ObjectSelect
                .query(Pkg.class)
                .where(NAME.eq(name))
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.PKG.name())
                .selectOne(context));
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if(null==getActive()) {
            setActive(true);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getName()) {
            if(!PATTERN_NAME.matcher(getName()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this, NAME.getName(), "malformed"));
            }
        }

    }

    public Optional<PkgUserRatingAggregate> getPkgUserRatingAggregate(Repository repository) {
        Preconditions.checkArgument(null != repository);
        return getPkgUserRatingAggregates()
                .stream()
                .filter(pura -> pura.getRepository().equals(repository))
                .collect(SingleCollector.optional());
    }

    /**
     * <p>The regular {$link #getModifyTimestamp} is at millisecond accuracy.  When dealing with second-accuracy
     * dates, this can cause anomalies.  For this reason, this method will provide the modify timestamp at
     * second accuracy.</p>
     */

    public Date getModifyTimestampSecondAccuracy() {
        return DateTimeHelper.secondAccuracyDate(getModifyTimestamp());
    }

    public UriComponentsBuilder appendPathSegments(UriComponentsBuilder builder) {
        return builder.pathSegment(getName());
    }

    public PkgProminence getPkgProminence(Repository repository) {
        return tryGetPkgProminence(repository)
                .orElseThrow(() -> new ObjectNotFoundException(
                        PkgProminence.class.getSimpleName(), repository.getCode()));
    }

    public Optional<PkgProminence> tryGetPkgProminence(Repository repository) {
        Preconditions.checkArgument(null != repository, "the repository must be provided");
        return getPkgProminences()
                .stream()
                .filter(pp -> pp.getRepository().equals(repository))
                .collect(SingleCollector.optional());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("name", getName())
                .build();
    }

}
