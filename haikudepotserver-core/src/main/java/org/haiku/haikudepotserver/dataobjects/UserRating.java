/*
 * Copyright 2014-2016, Andrew Lindesay
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
import org.haiku.haikudepotserver.dataobjects.auto._UserRating;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;

import java.util.*;

public class UserRating extends _UserRating implements CreateAndModifyTimestamped {

    public final static int MIN_USER_RATING = 0;
    public final static int MAX_USER_RATING = 5;

    public static Optional<UserRating> getByCode(ObjectContext context, String code) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be supplied");
        return Optional.ofNullable(ObjectSelect.query(UserRating.class).where(CODE.eq(code)).selectOne(context));
    }

    public static List<UserRating> findByUserAndPkg(ObjectContext context, User user, Pkg pkg) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != user, "the user must be supplied");
        Preconditions.checkArgument(null != pkg, "the pkg must be supplied");
        return ObjectSelect
                .query(UserRating.class)
                .where(PKG_VERSION.dot(PkgVersion.PKG).eq(pkg))
                .and(USER.eq(user))
                .and(ACTIVE.isTrue())
                .select(context);
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

        return ObjectSelect
                .query(UserRating.class)
                .where(USER.eq(user))
                .and(ACTIVE.isTrue())
                .and(PKG_VERSION.in(pkgVersions))
                .select(context);
    }

    public static Optional<UserRating> getByUserAndPkgVersion(ObjectContext context, User user, PkgVersion pkgVersion) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != user, "the user must be supplied");
        Preconditions.checkArgument(null != pkgVersion, "the pkgVersion must be supplied");

        return Optional.ofNullable(ObjectSelect.query(UserRating.class)
                .where(PKG_VERSION.eq(pkgVersion))
                .and(USER.eq(user))
                .selectOne(context));
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
                validationResult.addFailure(new BeanValidationFailure(this, RATING.getName(), "min"));
            }

            if(getRating() > MAX_USER_RATING) {
                validationResult.addFailure(new BeanValidationFailure(this, RATING.getName(), "max"));
            }
        }
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("code", getCode())
                .build();
    }

}
