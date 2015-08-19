/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.auto._UserRatingStability;
import org.haiku.haikudepotserver.dataobjects.support.Coded;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class UserRatingStability extends _UserRatingStability implements Coded {

    public final static String CODE_NOSTART="nostart";
    public final static String CODE_VERYUNSTABLE="veryunstable";
    public final static String CODE_UNSTABLEBUTUSABLE="unstablebutusable";
    public final static String CODE_MOSTLYSTABLE="mostlystable";
    public final static String CODE_STABLE="stable";

    public static Optional<UserRatingStability> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return ((List<UserRatingStability>) context.performQuery(new SelectQuery(
                        UserRatingStability.class,
                        ExpressionFactory.matchExp(UserRatingStability.CODE_PROPERTY, code))))
                .stream()
                .collect(SingleCollector.optional());
    }

    public static List<UserRatingStability> getAll(ObjectContext context) {
        Preconditions.checkNotNull(context);
        SelectQuery query = new SelectQuery(UserRatingStability.class);
        query.addOrdering(new Ordering(NAME_PROPERTY, SortOrder.ASCENDING));
        return (List<UserRatingStability>) context.performQuery(query);
    }


    public String getTitleKey() {
        return String.format("userRatingStability.%s.title", getCode());
    }

}
