/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PkgUserRatingAggregate;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class PkgUserRatingAggregate extends _PkgUserRatingAggregate {

    public static Optional<PkgUserRatingAggregate> getByPkgAndRepository(
            ObjectContext context,
            Pkg pkg,
            Repository repository) {
        return findByPkg(context, pkg)
                .stream()
                .filter(pura -> pura.getRepository().equals(repository))
                .collect(SingleCollector.optional());
    }

    public static List<PkgUserRatingAggregate> findByPkg(ObjectContext context, Pkg pkg) {
        Preconditions.checkNotNull(context);
        Preconditions.checkArgument(null != pkg, "a package is required to identify the pkg user rating aggregates to return");
        SelectQuery query = new SelectQuery(PkgUserRatingAggregate.class, ExpressionFactory.matchExp(PKG_PROPERTY, pkg));
        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.setCacheGroups(HaikuDepot.CacheGroup.PKG_USER_RATING_AGGREGATE.name());
        return (List<PkgUserRatingAggregate>) context.performQuery(query);
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if(null==getDerivedRatingSampleSize()) {
            setDerivedRatingSampleSize(0);
        }

        super.validateForInsert(validationResult);
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null!=getDerivedRatingSampleSize()) {
            if(getDerivedRatingSampleSize() <= 0) {
                validationResult.addFailure(new BeanValidationFailure(this,DERIVED_RATING_SAMPLE_SIZE_PROPERTY,"min"));
            }
        }

        if(null!=getDerivedRating()) {
            if(getDerivedRating() < 0) {
                validationResult.addFailure(new BeanValidationFailure(this,DERIVED_RATING_PROPERTY,"min"));
            }

            if(getDerivedRating() > 5) {
                validationResult.addFailure(new BeanValidationFailure(this,DERIVED_RATING_PROPERTY,"max"));
            }
        }

    }

}
