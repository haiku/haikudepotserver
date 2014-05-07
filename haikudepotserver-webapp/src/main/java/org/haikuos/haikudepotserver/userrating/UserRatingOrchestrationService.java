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
import org.apache.cayenne.query.EJBQLQuery;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

/**
 * <p>This service is able to provide support for non-trivial operations around user ratings.</p>
 */

@Service
public class UserRatingOrchestrationService {

    public SelectQuery prepare(ObjectContext context, UserRatingSearchSpecification search) {
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
                    new java.sql.Timestamp(now.minusDays(search.getDaysSinceCreated()).getMillis())));
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

        return new SelectQuery(UserRating.class, ExpressionHelper.andAll(expressions));
    }

    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        SelectQuery selectQuery = prepare(context, search);
        selectQuery.setFetchOffset(search.getOffset());
        selectQuery.setFetchLimit(search.getLimit());
        selectQuery.addOrdering(new Ordering(UserRating.CREATE_TIMESTAMP_PROPERTY, SortOrder.DESCENDING));

        //noinspection unchecked
        return context.performQuery(selectQuery);
    }

    public long total(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        SelectQuery selectQuery = prepare(context, search);
        List<Object> parameters = Lists.newArrayList();
        StringWriter buffer = new StringWriter();
        PrintWriter pw = new PrintWriter(buffer);
        selectQuery.getQualifier().encodeAsEJBQL(parameters, pw, "ur");
        pw.close();
        buffer.flush();

        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(ur) FROM UserRating AS ur WHERE " + buffer.toString());

        for(int i=0;i<parameters.size();i++) {
            ejbQuery.setParameter(i+1,parameters.get(i));
        }

        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(ejbQuery);

        switch(result.size()) {
            case 1:
                return result.get(0).longValue();

            default:
                throw new IllegalStateException("expected 1 row from count query, but got "+result.size());
        }
    }

}
