/*
 * Copyright 2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.thymeleaf;

import org.thymeleaf.context.IExpressionContext;
import org.thymeleaf.expression.IExpressionObjectFactory;

import java.util.Set;

/**
 * <p>Responsible for creating an instance of {@link ExpressionObject}.</p>
 */

public class ExpressionObjectFactory implements IExpressionObjectFactory {

    private final static String OBJECT_NAME = "hds";

    @Override
    public Set<String> getAllExpressionObjectNames() {
        return Set.of(OBJECT_NAME);
    }

    @Override
    public Object buildObject(IExpressionContext context, String expressionObjectName) {
        if (expressionObjectName.equals(OBJECT_NAME)) {
            return new ExpressionObject();
        }
        return null;
    }

    @Override
    public boolean isCacheable(String expressionObjectName) {
        return true;
    }

}
