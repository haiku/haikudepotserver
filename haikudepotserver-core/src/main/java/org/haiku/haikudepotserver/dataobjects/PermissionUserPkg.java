/*
 * Copyright 2014-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PermissionUserPkg;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRule;

import java.time.Clock;
import java.util.Optional;

public class PermissionUserPkg extends _PermissionUserPkg implements AuthorizationPkgRule {

    public static Optional<PermissionUserPkg> getByPermissionUserAndPkg(
            ObjectContext context,
            Permission permission,
            User user,
            Pkg pkg) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(null != permission, "the context must be provided");
        Preconditions.checkArgument(null != user, "the user must be provided");

        return Optional.ofNullable(ObjectSelect.query(PermissionUserPkg.class)
                .where(PERMISSION.eq(permission))
                .and(USER.eq(user))
                .and(PKG.eq(pkg))
                .selectOne(context));
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if (null == getCreateTimestamp()) {
            setCreateTimestamp(new java.sql.Timestamp(Clock.systemUTC().millis()));
        }

        super.validateForInsert(validationResult);
    }

}
