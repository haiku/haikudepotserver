/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>This service is able to provide support for non-trivial operations around user ratings.</p>
 */

@Service
public class UserRatingOrchestrationService {

    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        DateTime now = new DateTime();

        // build up a list of expressions

        List<Expression> expressions = Lists.newArrayList();

        if (!search.getIncludeInactive()) {
            expressions.add(ExpressionFactory.matchExp(
                    UserRating.ACTIVE_PROPERTY,
                    Boolean.TRUE));
        }

        if (null != search.getDaysSinceCreated()) {
            expressions.add(ExpressionFactory.greaterExp(
                    UserRating.CREATE_TIMESTAMP_PROPERTY,
                    now.minusDays(search.getDaysSinceCreated()).toDate()));
        }

        if (null != search.getPkg() && null == search.getPkgVersion()) {
            expressions.add(ExpressionFactory.matchExp(
                    UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY,
                    search.getPkg()));
        }

        if (null != search.getArchitecture() && null == search.getPkgVersion()) {
            expressions.add(ExpressionFactory.matchExp(
                    UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.ARCHITECTURE_PROPERTY,
                    search.getArchitecture()));
        }

        if (null != search.getPkgVersion()) {
            expressions.add(ExpressionFactory.matchExp(
                    UserRating.PKG_VERSION_PROPERTY,
                    search.getPkgVersion()));
        }

        if (null != search.getUser()) {
            expressions.add(ExpressionFactory.matchExp(
                    UserRating.USER_PROPERTY,
                    search.getUser()));
        }

        SelectQuery selectQuery = new SelectQuery(UserRating.class, ExpressionHelper.andAll(expressions));
        selectQuery.setFetchOffset(search.getOffset());
        selectQuery.setFetchLimit(search.getLimit());
        selectQuery.addOrdering(new Ordering(UserRating.CREATE_TIMESTAMP_PROPERTY, SortOrder.DESCENDING));

        //noinspection unchecked
        return context.performQuery(selectQuery);
    }

}
