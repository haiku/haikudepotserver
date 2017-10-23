/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.api1.model.miscellaneous.GetAllUserRatingStabilitiesResult;
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
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(code), "the code must be supplied");
        return getAll(context).stream().filter((urs) -> urs.getCode().equals(code)).findFirst();
    }

    public static List<UserRatingStability> getAll(ObjectContext context) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        return ObjectSelect.query(UserRatingStability.class)
                .orderBy(NAME.asc())
                .sharedCache()
                .select(context);
    }

    public String getTitleKey() {
        return String.format("userRatingStability.%s.title", getCode());
    }

}
