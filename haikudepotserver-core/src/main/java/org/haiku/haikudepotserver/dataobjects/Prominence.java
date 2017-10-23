/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.haiku.haikudepotserver.dataobjects.auto._Prominence;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class Prominence extends _Prominence {

    public final static Integer ORDERING_LAST = 1000;

    public static Optional<Prominence> getByOrdering(ObjectContext context, Integer ordering) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkState(null != ordering && ordering >= 0, "bad ordering");
        return getAll(context).stream().filter((p) -> p.getOrdering().equals(ordering)).findFirst();
    }

    public static List<Prominence> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        return ObjectSelect.query(Prominence.class).orderBy(ORDERING.asc()).sharedCache().select(context);
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.SHORT_PREFIX_STYLE)
                .append("ordering", getOrdering())
                .build();
    }

}
