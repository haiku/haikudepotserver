/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.apache.cayenne.validation.SimpleValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

import java.util.List;
import java.util.regex.Pattern;

public class PkgVersion extends _PkgVersion implements CreateAndModifyTimestamped {

    public final static Pattern MAJOR_PATTERN = Pattern.compile("^[\\w_]+$");
    public final static Pattern MINOR_PATTERN = Pattern.compile("^[\\w_]+$");
    public final static Pattern MICRO_PATTERN = Pattern.compile("^[\\w_.]+$");
    public final static Pattern PRE_RELEASE_PATTERN = Pattern.compile("^[\\w_.]+$");

    public static List<Ordering> versionOrdering() {
        List<Ordering> result = Lists.newArrayList();
        result.add(new Ordering(PkgVersion.MAJOR_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.MINOR_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.MICRO_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.PRE_RELEASE_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        result.add(new Ordering(PkgVersion.REVISION_PROPERTY, SortOrder.DESCENDING_INSENSITIVE));
        return result;
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

        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<PkgVersion>) context.performQuery(query),
                null));
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null != getMajor()) {
            if(!MAJOR_PATTERN.matcher(getMajor()).matches()) {
                validationResult.addFailure(new SimpleValidationFailure(this,MAJOR_PROPERTY + ".malformed"));
            }
        }

        if(null != getMinor()) {
            if(!MINOR_PATTERN.matcher(getMinor()).matches()) {
                validationResult.addFailure(new SimpleValidationFailure(this,MINOR_PROPERTY + ".malformed"));
            }
        }

        if(null != getMicro()) {
            if(!MICRO_PATTERN.matcher(getMicro()).matches()) {
                validationResult.addFailure(new SimpleValidationFailure(this,MICRO_PROPERTY + ".malformed"));
            }
        }

        if(null != getPreRelease()) {
            if(!PRE_RELEASE_PATTERN.matcher(getPreRelease()).matches()) {
                validationResult.addFailure(new SimpleValidationFailure(this,PRE_RELEASE_PROPERTY + ".malformed"));
            }
        }

        if(null != getRevision()) {
            if(getRevision().intValue() <= 0) {
                validationResult.addFailure(new SimpleValidationFailure(this,REVISION_PROPERTY + ".lessThanEqualZero"));
            }
        }

    }

}
