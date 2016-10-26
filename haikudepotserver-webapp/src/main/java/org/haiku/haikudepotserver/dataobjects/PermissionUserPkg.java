/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.ValidationResult;
import org.haiku.haikudepotserver.dataobjects.auto._PermissionUserPkg;
import org.haiku.haikudepotserver.security.model.AuthorizationPkgRule;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
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

        return ((List<PermissionUserPkg>) context.performQuery(new SelectQuery(
                PermissionUserPkg.class,
                ExpressionFactory.matchExp(PermissionUserPkg.PERMISSION_PROPERTY, permission).andExp(
                        ExpressionFactory.matchExp(PermissionUserPkg.USER_PROPERTY, user).andExp(
                                ExpressionFactory.matchExp(PermissionUserPkg.PKG_PROPERTY, pkg)
                        )
                )))).stream().collect(SingleCollector.optional());
    }

    @Override
    public void validateForInsert(ValidationResult validationResult) {

        if (null == getCreateTimestamp()) {
            setCreateTimestamp(new java.util.Date());
        }

        super.validateForInsert(validationResult);
    }

}
