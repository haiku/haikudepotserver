/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.haiku.haikudepotserver.dataobjects.auto._Permission;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class Permission extends _Permission {

    public static List<Permission> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        SelectQuery query = new SelectQuery(Permission.class);
        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);
        query.addOrdering(new Ordering(NAME_PROPERTY, SortOrder.ASCENDING));
        return (List<Permission>) context.performQuery(query);
    }

    public static Optional<Permission> getByCode(ObjectContext context, final String code) {
        Preconditions.checkArgument(null != context, "the context must be provided");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the permission code must be provided");
        return getAll(context).stream().filter(p -> p.getCode().equals(code)).collect(SingleCollector.optional());
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("code", getCode())
                .build();
    }
}
