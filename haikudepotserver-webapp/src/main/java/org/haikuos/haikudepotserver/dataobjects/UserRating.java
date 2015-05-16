/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._UserRating;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.support.SingleCollector;

import java.util.*;

public class UserRating extends _UserRating implements CreateAndModifyTimestamped {

    public static Optional<UserRating> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return ((List<UserRating>) context.performQuery(new SelectQuery(
                        UserRating.class,
                        ExpressionFactory.matchExp(UserRating.CODE_PROPERTY, code))))
                .stream()
                .collect(SingleCollector.optional());
    }

    public static List<UserRating> findByUserAndPkg(ObjectContext context, User user, Pkg pkg) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(pkg);

        return context.performQuery(new SelectQuery(
                UserRating.class,
                ExpressionFactory.matchExp(UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY, pkg)
                .andExp(ExpressionFactory.matchExp(UserRating.USER_PROPERTY, user))
                .andExp(ExpressionFactory.matchExp(UserRating.ACTIVE_PROPERTY, Boolean.TRUE))));
    }

    public static List<UserRating> getByUserAndPkgVersions(
            ObjectContext context,
            User user,
            Collection<PkgVersion> pkgVersions) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(pkgVersions);

        if(pkgVersions.isEmpty()) {
            return Collections.emptyList();
        }

        return context.performQuery(new SelectQuery(
                UserRating.class,
                ExpressionFactory.inExp(UserRating.PKG_VERSION_PROPERTY, pkgVersions)
                        .andExp(ExpressionFactory.matchExp(UserRating.USER_PROPERTY, user))
                        .andExp(ExpressionFactory.matchExp(UserRating.ACTIVE_PROPERTY, Boolean.TRUE))));
    }

    public static Optional<UserRating> getByUserAndPkgVersion(ObjectContext context, User user, PkgVersion pkgVersion) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(user);
        Preconditions.checkNotNull(pkgVersion);

        return ((List<UserRating>) context.performQuery(new SelectQuery(
                        UserRating.class,
                        ExpressionFactory.matchExp(UserRating.PKG_VERSION_PROPERTY, pkgVersion)
                                .andExp(ExpressionFactory.matchExp(UserRating.USER_PROPERTY, user)))))
                .stream()
                .collect(SingleCollector.optional());
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

    @Override
    public String toString() {
        return "userrating; " + getCode();
    }

}
