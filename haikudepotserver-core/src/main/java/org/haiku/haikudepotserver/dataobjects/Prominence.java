/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.haiku.haikudepotserver.dataobjects.auto._Prominence;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class Prominence extends _Prominence {

    public final static Integer ORDERING_LAST = 1000;

    public static Optional<Prominence> getByOrdering(ObjectContext context, Integer ordering) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkState(null != ordering && ordering >= 0, "bad ordering");
        return ((List<Prominence>) context.performQuery(new SelectQuery(
                        Prominence.class,
                        ExpressionFactory.matchExp(Prominence.ORDERING_PROPERTY, ordering)))).stream().collect(SingleCollector.optional());
    }

    public static List<Prominence> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        SelectQuery query = new SelectQuery(Prominence.class);
        query.addOrdering(new Ordering(ORDERING_PROPERTY, SortOrder.ASCENDING));
        return (List<Prominence>) context.performQuery(query);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this)
                .append("ordering", getOrdering())
                .build();
    }

}
