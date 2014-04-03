/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.repository;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.Repository;
import org.haikuos.haikudepotserver.repository.model.RepositorySearchSpecification;
import org.haikuos.haikudepotserver.support.cayenne.LikeHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * <p>This service provides non-trivial operations and processes around repositories.</p>
 */

@Service
public class RepositoryOrchestrationService {

    protected static Logger logger = LoggerFactory.getLogger(RepositoryOrchestrationService.class);

    // ------------------------------
    // SEARCH

    public List<Repository> search(ObjectContext context, RepositorySearchSpecification search) {
        Preconditions.checkNotNull(search);
        Preconditions.checkNotNull(context);
        Preconditions.checkState(search.getOffset() >= 0);
        Preconditions.checkState(search.getLimit() > 0);

        List<Expression> expressions = Lists.newArrayList();

        if(null!=search.getExpression()) {
            switch(search.getExpressionType()) {
                case CONTAINS:
                    expressions.add(ExpressionFactory.likeExp(
                            Repository.CODE_PROPERTY,
                            "%" + LikeHelper.ESCAPER.escape(search.getExpression()) + "%"));
                    break;

                default:
                    throw new IllegalStateException("unsupported expression type; "+search.getExpressionType());
            }
        }

        if(!search.getIncludeInactive()) {
            expressions.add(ExpressionFactory.matchExp(Repository.ACTIVE_PROPERTY, Boolean.TRUE));
        }

        Expression expression = null;

        for(Expression e : expressions) {
            if(null==expression) {
                expression = e;
            }
            else {
                expression = expression.andExp(e);
            }
        }

        SelectQuery selectQuery = new SelectQuery(Repository.class, expression);
        selectQuery.setFetchLimit(search.getLimit());
        selectQuery.setFetchOffset(search.getOffset());
        selectQuery.addOrdering(new Ordering(Repository.CODE_PROPERTY, SortOrder.ASCENDING));

        return (List<Repository>) context.performQuery(selectQuery);
    }


}
