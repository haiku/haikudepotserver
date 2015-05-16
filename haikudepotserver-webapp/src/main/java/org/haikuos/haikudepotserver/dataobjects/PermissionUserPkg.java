/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.validation.ValidationResult;
import org.haikuos.haikudepotserver.dataobjects.auto._PermissionUserPkg;
import org.haikuos.haikudepotserver.security.model.AuthorizationPkgRule;
import org.haikuos.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class PermissionUserPkg extends _PermissionUserPkg implements AuthorizationPkgRule {

    public static Optional<PermissionUserPkg> getByPermissionUserAndPkg(
            ObjectContext context,
            Permission permission,
            User user,
            Pkg pkg) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(permission);
        Preconditions.checkNotNull(user);
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
