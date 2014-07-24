/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.auto._Permission;

import java.util.List;

public class Permission extends _Permission {

    public static Optional<Permission> getByCode(ObjectContext context, String code) {
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<Permission>) context.performQuery(new SelectQuery(
                        Permission.class,
                        ExpressionFactory.matchExp(Permission.CODE_PROPERTY, code))),
                null));
    }

    @Override
    public String toString() {
        return "permission;"+getCode();
    }

}
