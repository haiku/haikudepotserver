/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.repository;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.EJBQLQuery;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.haikuos.haikudepotserver.support.LikeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>This service provides non-trivial operations and processes around repositories.</p>
 */

@Service
public class RepositoryOrchestrationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryOrchestrationService.class);

    // ------------------------------
    // SEARCH

    private String prepareWhereClause(
            List<Object> parameterAccumulator,
            ObjectContext context,
            RepositorySearchSpecification search) {

        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<String> whereExpressions = Lists.newArrayList();

        if(null!=search.getExpression()) {
            switch(search.getExpressionType()) {

                case CONTAINS:
                    parameterAccumulator.add("%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%");
                    whereExpressions.add("LOWER(r." + Repository.CODE_PROPERTY + ") LIKE ?" + parameterAccumulator.size() + " ESCAPE '|'");
                   break;

                default:
                    throw new IllegalStateException("unsupported expression type; "+search.getExpressionType());

            }
        }

        if(!search.getIncludeInactive()) {
            whereExpressions.add("r." + Repository.ACTIVE_PROPERTY + " = true");
        }

        return Joiner.on(" AND ").join(whereExpressions);
    }

    public List<Repository> search(ObjectContext context, RepositorySearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        List<Object> parameterAccumulator = Lists.newArrayList();
        String ejbql = "SELECT r FROM " + Repository.class.getSimpleName() + " AS r WHERE " + prepareWhereClause(parameterAccumulator, context, search) + " ORDER BY r." + Repository.CODE_PROPERTY + " ASC";
        EJBQLQuery ejbqlQuery = new EJBQLQuery(ejbql);

        for(int i=0;i<parameterAccumulator.size();i++) {
            ejbqlQuery.setParameter(i+1, parameterAccumulator.get(i));
        }

        ejbqlQuery.setFetchLimit(search.getLimit());
        ejbqlQuery.setFetchOffset(search.getOffset());

        //noinspection unchecked
        return (List<Repository>) context.performQuery(ejbqlQuery);
    }

    public long total(ObjectContext context, RepositorySearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);

        List<Object> parameters = Lists.newArrayList();
        EJBQLQuery ejbQuery = new EJBQLQuery("SELECT COUNT(r) FROM Repository AS r WHERE " + prepareWhereClause(parameters, context, search));

        for(int i=0;i<parameters.size();i++) {
            ejbQuery.setParameter(i+1, parameters.get(i));
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
