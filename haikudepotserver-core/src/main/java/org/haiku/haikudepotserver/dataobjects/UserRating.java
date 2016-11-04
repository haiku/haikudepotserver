/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.BeanValidationFailure;
import org.apache.cayenne.validation.ValidationResult;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.haiku.haikudepotserver.dataobjects.auto._UserRating;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.*;

public class UserRating extends _UserRating implements CreateAndModifyTimestamped {

    public final static int MIN_USER_RATING = 0;
    public final static int MAX_USER_RATING = 5;

    public static Optional<UserRating> getByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be supplied");
        return ((List<UserRating>) context.performQuery(new SelectQuery(
                        UserRating.class,
                        ExpressionFactory.matchExp(UserRating.CODE_PROPERTY, code))))
                .stream()
                .collect(SingleCollector.optional());
    }

    public static List<UserRating> findByUserAndPkg(ObjectContext context, User user, Pkg pkg) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != user, "the user must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");

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
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != user, "the user must be supplied");
        Preconditions.checkArgument(null != pkgVersions, "the pkgVersions must be supplied");

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
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != user, "the user must be supplied");
        Preconditions.checkArgument(null != pkgVersion, "the pkgVersion must be supplied");

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
            if(getRating() < MIN_USER_RATING) {
                validationResult.addFailure(new BeanValidationFailure(this,RATING_PROPERTY,"min"));
            }

            if(getRating() > MAX_USER_RATING) {
                validationResult.addFailure(new BeanValidationFailure(this,RATING_PROPERTY,"max"));
            }
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("code", getCode())
                .build();
    }

}
