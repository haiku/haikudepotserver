package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgUserRatingAggregate;

import java.util.List;

public class PkgUserRatingAggregate extends _PkgUserRatingAggregate {

    public static List<PkgUserRatingAggregate> findByPkg(ObjectContext context, Pkg pkg) {
        Preconditions.checkNotNull(context);
        Preconditions.checkArgument(null != pkg, "a package is required to identify the pkg user rating aggregates to return");
        SelectQuery query = new SelectQuery(PkgUserRatingAggregate.class, ExpressionFactory.matchExp(PKG_PROPERTY, pkg));
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
