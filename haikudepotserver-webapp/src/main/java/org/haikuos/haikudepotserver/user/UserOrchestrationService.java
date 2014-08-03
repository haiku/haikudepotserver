/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.support.cayenne.LikeHelper;
import org.haikuos.haikudepotserver.user.model.UserSearchSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>This service undertakes non-trivial operations on users.</p>
 */

@Service
public class UserOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserOrchestrationService.class);

    // ------------------------------
    // SEARCH

    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            UserSearchSpecification search) {

        Preconditions.checkNotNull(parameterAccumulator);
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        List<String> parts = Lists.newArrayList();

        if(!Strings.isNullOrEmpty(search.getExpression())) {
            switch(search.getExpressionType()) {

                case CONTAINS:
                    parts.add("LOWER(u.nickname) LIKE ?" + (parameterAccumulator.size() + 1));
                    parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                    break;

                default:
                    throw new IllegalStateException("unknown expression type " + search.getExpressionType().name());

            }
        }

        if(!search.getIncludeInactive()) {
            parts.add("u.active = ?" + (parameterAccumulator.size() + 1));
            parameterAccumulator.add(Boolean.TRUE);
        }

        return Joiner.on(" AND ").join(parts);
    }

    /**
     * <p>Undertakes a search for users.</p>
     */

    public List<User> search(
            ObjectContext context,
            UserSearchSpecification searchSpecification) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(searchSpecification);

        StringBuilder queryBuilder = new StringBuilder("SELECT u FROM User AS u");
        List<Object> parameters = Lists.newArrayList();
        String where = prepareWhereClause(parameters, context, searchSpecification);

        if(!Strings.isNullOrEmpty(where)) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(where);
        }

        queryBuilder.append(" ORDER BY u.nickname ASC");

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i+1, parameters.get(i));
        }

        query.setFetchOffset(searchSpecification.getOffset());
        query.setFetchLimit(searchSpecification.getLimit());

        return (List<User>) context.performQuery(query);
    }

    /**
     * <p>Find out the total number of results that would be yielded from
     * a search if the search were not constrained.</p>
     */

    public long total(
            ObjectContext context,
            UserSearchSpecification searchSpecification) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(searchSpecification);

        StringBuilder queryBuilder = new StringBuilder("SELECT COUNT(u) FROM User u");
        List<Object> parameters = Lists.newArrayList();
        String where = prepareWhereClause(parameters, context, searchSpecification);

        if(!Strings.isNullOrEmpty(where)) {
            queryBuilder.append(" WHERE ");
            queryBuilder.append(where);
        }

        EJBQLQuery query = new EJBQLQuery(queryBuilder.toString());

        for (int i = 0; i < parameters.size(); i++) {
            query.setParameter(i+1, parameters.get(i));
        }

        List<Long> result = (List<Long>) context.performQuery(query);

        switch(result.size()) {

            case 1:
                return result.get(0).longValue();

            default:
                throw new IllegalStateException("the result should have contained a single long result");

        }
    }

    // ------------------------------


}
