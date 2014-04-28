/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.support.VersionCoordinates;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;

import java.util.List;
import java.util.regex.Pattern;

public class PkgVersion extends _PkgVersion implements CreateAndModifyTimestamped {

    public final static Pattern MAJOR_PATTERN = Pattern.compile("^[\\w_]+$");
    public final static Pattern MINOR_PATTERN = Pattern.compile("^[\\w_]+$");
    public final static Pattern MICRO_PATTERN = Pattern.compile("^[\\w_.]+$");
    public final static Pattern PRE_RELEASE_PATTERN = Pattern.compile("^[\\w_.]+$");

    // TODO; could there be a problem here with alpha ordering of version numbers???
    public static List<Ordering> versionOrdering() {
        List<Ordering> result = Lists.newArrayList();
        result.add(new Ordering(PkgVersion.MAJOR_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.MINOR_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.MICRO_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.PRE_RELEASE_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.REVISION_PROPERTY, SortOrder.DESCENDING));
        return result;
    }

    public static Optional<PkgVersion> getForPkg(
            ObjectContext context,
            Pkg pkg,
            Architecture architecture,
            VersionCoordinates versionCoordinates) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkNotNull(architecture);
        Preconditions.checkNotNull(versionCoordinates);

        SelectQuery query = new SelectQuery(
                PkgVersion.class,
                ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkg).andExp(
                        ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE)).andExp(
                        ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, architecture)).andExp(
                        ExpressionHelper.toExpression(versionCoordinates))
        );

        query.addOrderings(versionOrdering());

        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<PkgVersion>) context.performQuery(query),
                null));
    }

    public static Optional<PkgVersion> getLatestForPkg(
            ObjectContext context,
            Pkg pkg,
            List<Architecture> architectures) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(pkg);
        Preconditions.checkNotNull(architectures);
        Preconditions.checkState(!architectures.isEmpty());

        Expression architectureExpression = null;

        for(Architecture architecture : architectures) {
            if(null==architectureExpression) {
                architectureExpression = ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, architecture);
            }
            else {
                architectureExpression = architectureExpression.orExp(
                        ExpressionFactory.matchExp(PkgVersion.ARCHITECTURE_PROPERTY, architecture));
            }
        }

        SelectQuery query = new SelectQuery(
                PkgVersion.class,
                ExpressionFactory.matchExp(PkgVersion.PKG_PROPERTY, pkg).andExp(
                        ExpressionFactory.matchExp(PkgVersion.ACTIVE_PROPERTY, Boolean.TRUE)).andExp(
                        architectureExpression));

        query.setFetchLimit(1);
        query.addOrderings(versionOrdering());

        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<PkgVersion>) context.performQuery(query),
                null));
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if(null==getViewCounter()) {
            setViewCounter(0l);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getMajor()) {
            if(!MAJOR_PATTERN.matcher(getMajor()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,MAJOR_PROPERTY,"malformed"));
            }
        }

        if(null != getMinor()) {
            if(!MINOR_PATTERN.matcher(getMinor()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,MINOR_PROPERTY,"malformed"));
            }
        }

        if(null != getMicro()) {
            if(!MICRO_PATTERN.matcher(getMicro()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,MICRO_PROPERTY,"malformed"));
            }
        }

        if(null != getPreRelease()) {
            if(!PRE_RELEASE_PATTERN.matcher(getPreRelease()).matches()) {
                validationResult.addFailure(new BeanValidationFailure(this,PRE_RELEASE_PROPERTY,"malformed"));
            }
        }

        if(null != getRevision()) {
            if(getRevision().intValue() <= 0) {
                validationResult.addFailure(new BeanValidationFailure(this,REVISION_PROPERTY,"lessThanEqualZero"));
            }
        }

        if(getViewCounter().longValue() < 0) {
            validationResult.addFailure(new BeanValidationFailure(this,VIEW_COUNTER_PROPERTY,"min"));
        }

    }

    public void incrementViewCounter() {
        setViewCounter(getViewCounter()+1);
    }

    /**
     * <p>This will try to find localized data for the pkg version for the supplied natural language.  Because
     * English language data is hard-coded into the package payload, english will always be available.</p>
     */

    public Optional<PkgVersionLocalization> getPkgVersionLocalization(final NaturalLanguage naturalLanguage) {
        return getPkgVersionLocalization(naturalLanguage.getCode());
    }

    /**
     * <p>This will try to find localized data for the pkg version for the supplied natural language.  Because
     * English language data is hard-coded into the package payload, english will always be available.</p>
     */

    public Optional<PkgVersionLocalization> getPkgVersionLocalization(final String naturalLanguageCode) {
        Preconditions.checkState(!Strings.isNullOrEmpty(naturalLanguageCode));

        return Iterables.tryFind(
                getPkgVersionLocalizations(),
                new Predicate<PkgVersionLocalization>() {
                    @Override
                    public boolean apply(PkgVersionLocalization input) {
                        return input.getNaturalLanguage().getCode().equals(naturalLanguageCode);
                    }
                }
        );
    }

    public VersionCoordinates toVersionCoordinates() {
        return new VersionCoordinates(
                getMajor(),
                getMinor(),
                getMicro(),
                getPreRelease(),
                getRevision());
    }

    @Override
    public String toString() {
        return toVersionCoordinates().toString();
    }

}
