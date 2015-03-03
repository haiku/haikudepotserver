/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.auto._Permission;

import java.util.List;

public class Permission extends _Permission {

    public static List<Permission> getAll(ObjectContext context) {
        Preconditions.checkNotNull(context);
        SelectQuery query = new SelectQuery(Permission.class);
        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.addOrdering(new Ordering(NAME_PROPERTY, SortOrder.ASCENDING));
        return (List<Permission>) context.performQuery(query);
    }

    public static Optional<Permission> getByCode(ObjectContext context, final String code) {
        Preconditions.checkArgument(null!=context, "the context must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the permission code must be provided");

        return Iterables.tryFind(
                getAll(context),
                new Predicate<Permission>() {
                    @Override
                    public boolean apply(Permission input) {
                        return input.getCode().equals(code);
                    }
                }
        );
    }

    @Override
    public String toString() {
        return "permission;"+getCode();
    }

}
