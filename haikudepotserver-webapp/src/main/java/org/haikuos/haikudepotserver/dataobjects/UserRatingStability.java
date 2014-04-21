/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.auto._UserRatingStability;
import org.haikuos.haikudepotserver.dataobjects.support.Coded;

import java.util.List;

public class UserRatingStability extends _UserRatingStability implements Coded {

    public final static String CODE_NOSTART="nostart";
    public final static String CODE_VERYUNSTABLE="veryunstable";
    public final static String CODE_UNSTABLEBUTUSABLE="unstablebutusable";
    public final static String CODE_MOSTLYSTABLE="mostlystable";
    public final static String CODE_STABLE="stable";

    public static Optional<UserRatingStability> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<UserRatingStability>) context.performQuery(new SelectQuery(
                        UserRatingStability.class,
                        ExpressionFactory.matchExp(UserRatingStability.CODE_PROPERTY, code))),
                null
        ));
    }

    public static List<UserRatingStability> getAll(ObjectContext context) {
        Preconditions.checkNotNull(context);
        SelectQuery query = new SelectQuery(UserRatingStability.class);
        query.addOrdering(new Ordering(NAME_PROPERTY, SortOrder.ASCENDING));
        return (List<UserRatingStability>) context.performQuery(query);
    }

}
