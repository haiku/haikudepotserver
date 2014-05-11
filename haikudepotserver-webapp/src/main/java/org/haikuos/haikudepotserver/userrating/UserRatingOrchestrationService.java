/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.haikuos.haikudepotserver.dataobjects.PkgVersion;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.joda.time.DateTime;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>This service is able to provide support for non-trivial operations around user ratings.</p>
 */

@Service
public class UserRatingOrchestrationService {

    // ------------------------------
    // SEARCH

    // [apl 11.may.2014]
    // SelectQuery has no means of getting a count.  This is a bit of an annoying limitation, but can be worked around
    // by using EJBQL.  However converting from an Expression to EJBQL has a problem (see CAY-1932) so for the time
    // being, just use EJBQL directly by assembling strings and convert back later.

    // NOTE; raw EJBQL can be replaced with Expressions once CAY-1932 is fixed.
    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            UserRatingSearchSpecification search) {

        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        DateTime now = new DateTime();

        List<String> whereExpressions = Lists.newArrayList();

        if (!search.getIncludeInactive()) {
            whereExpressions.add("ur." + UserRating.ACTIVE_PROPERTY + " = true");
        }

        if (null != search.getDaysSinceCreated()) {
            parameterAccumulator.add(new java.sql.Timestamp(now.minusDays(search.getDaysSinceCreated()).getMillis()));
            whereExpressions.add("ur." + UserRating.CREATE_TIMESTAMP_PROPERTY + " > ?" + parameterAccumulator.size());
        }

        if (null != search.getPkg() && null == search.getPkgVersion()) {
            parameterAccumulator.add(search.getPkg());
            whereExpressions.add("ur." + UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if (null != search.getArchitecture() && null == search.getPkgVersion()) {
            parameterAccumulator.add(search.getArchitecture());
            whereExpressions.add("ur." + UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.ARCHITECTURE_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if (null != search.getPkgVersion()) {
            parameterAccumulator.add(search.getPkgVersion());
            whereExpressions.add("ur." + UserRating.PKG_VERSION_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        if (null != search.getUser()) {
            parameterAccumulator.add(search.getUser());
            whereExpressions.add("ur." + UserRating.USER_PROPERTY + " = ?" + parameterAccumulator.size());
        }

        return Joiner.on(" AND ").join(whereExpressions);

    }

    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<Object> parameters = Lists.newArrayList();
        EJBQLQuery query = new EJBQLQuery("SELECT ur FROM " + UserRating.class.getSimpleName() + " AS ur WHERE " + prepareWhereClause(parameters, context, search) + " ORDER BY ur." + UserRating.CREATE_TIMESTAMP_PROPERTY + " DESC");
        query.setFetchOffset(search.getOffset());
        query.setFetchLimit(search.getLimit());

        for(int i=0;i<parameters.size();i++) {
            query.setParameter(i+1,parameters.get(i));
        }

        //noinspection unchecked
        return context.performQuery(query);
    }

    public long total(ObjectContext context, UserRatingSearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<Object> parameters = Lists.newArrayList();
        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(ur) FROM UserRating AS ur WHERE " + prepareWhereClause(parameters, context, search));

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

//    public SelectQuery prepare(ObjectContext context, UserRatingSearchSpecification search) {
//        Preconditions.checkNotNull(search);
//        Preconditions.checkNotNull(context);
//        DateTime now = new DateTime();
//
//        // build up a list of expressions
//
//        List<Expression> expressions = Lists.newArrayList();
//
//        if (!search.getIncludeInactive()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.ACTIVE_PROPERTY,
//                    Boolean.TRUE));
//        }
//
//        if (null != search.getDaysSinceCreated()) {
//            expressions.add(ExpressionFactory.greaterExp(
//                    UserRating.CREATE_TIMESTAMP_PROPERTY,
//                    new java.sql.Timestamp(now.minusDays(search.getDaysSinceCreated()).getMillis())));
//        }
//
//        if (null != search.getPkg() && null == search.getPkgVersion()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.PKG_PROPERTY,
//                    search.getPkg()));
//        }
//
//        if (null != search.getArchitecture() && null == search.getPkgVersion()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.PKG_VERSION_PROPERTY + "." + PkgVersion.ARCHITECTURE_PROPERTY,
//                    search.getArchitecture()));
//        }
//
//        if (null != search.getPkgVersion()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.PKG_VERSION_PROPERTY,
//                    search.getPkgVersion()));
//        }
//
//        if (null != search.getUser()) {
//            expressions.add(ExpressionFactory.matchExp(
//                    UserRating.USER_PROPERTY,
//                    search.getUser()));
//        }
//
//        return new SelectQuery(UserRating.class, ExpressionHelper.andAll(expressions));
//    }
//
//    public List<UserRating> search(ObjectContext context, UserRatingSearchSpecification search) {
//        Preconditions.checkNotNull(search);
//        Preconditions.checkNotNull(context);
//
//        SelectQuery selectQuery = prepare(context, search);
//        selectQuery.setFetchOffset(search.getOffset());
//        selectQuery.setFetchLimit(search.getLimit());
//        selectQuery.addOrdering(new Ordering(UserRating.CREATE_TIMESTAMP_PROPERTY, SortOrder.DESCENDING));
//
//        //noinspection unchecked
//        return context.performQuery(selectQuery);
//    }
//
//    public long total(ObjectContext context, UserRatingSearchSpecification search) {
//        Preconditions.checkNotNull(search);
//        Preconditions.checkNotNull(context);
//
//        SelectQuery selectQuery = prepare(context, search);
//        List<Object> parameters = Lists.newArrayList();
//        String ejbql = selectQuery.getQualifier().toEJBQL(parameters, "ur");
//
//        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(ur) FROM UserRating AS ur WHERE " + ejbql);
//
//        for(int i=0;i<parameters.size();i++) {
//            ejbQuery.setParameter(i+1,parameters.get(i));
//        }
//
//        @SuppressWarnings("unchecked") List<Number> result = context.performQuery(ejbQuery);
//
//        switch(result.size()) {
//            case 1:
//                return result.get(0).longValue();
//
//            default:
//                throw new IllegalStateException("expected 1 row from count query, but got "+result.size());
//        }
//    }

}
