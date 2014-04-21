/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._UserRating;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

import java.util.List;
import java.util.UUID;

public class UserRating extends _UserRating implements CreateAndModifyTimestamped {

    public static Optional<UserRating> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<UserRating>) context.performQuery(new SelectQuery(
                        UserRating.class,
                        ExpressionFactory.matchExp(UserRating.CODE_PROPERTY, code))),
                null
        ));
    }

    public static Optional<UserRating> getByUserAndPkgVersion(ObjectContext context, User user, PkgVersion pkgVersion) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(pkgVersion);

        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<UserRating>) context.performQuery(new SelectQuery(
                        UserRating.class,
                        ExpressionFactory.matchExp(UserRating.PKG_VERSION_PROPERTY, pkgVersion)
                                .andExp(ExpressionFactory.matchExp(UserRating.USER_PROPERTY, user)))),
                null
        ));
    }

    // calback managed by cayenne
    public void onPostAdd() {

        if(null==getCode()) {
            setCode(UUID.randomUUID().toString());
        }

        if(null==getActive()) {
            setActive(Boolean.TRUE);
        }

        // create and modify timestamp handled by listener.
    }

    @Override
    protected void validateForSave(ValidationResult validationResult) {
        super.validateForSave(validationResult);

        if(null!=getRating()) {
            if(getRating() < 0) {
                validationResult.addFailure(new BeanValidationFailure(this,RATING_PROPERTY,"min"));
            }

            if(getRating() > 5) {
                validationResult.addFailure(new BeanValidationFailure(this,RATING_PROPERTY,"max"));
            }
        }
    }

}
