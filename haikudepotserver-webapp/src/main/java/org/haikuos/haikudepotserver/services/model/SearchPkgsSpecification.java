/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.services.model;

import org.haikuos.haikudepotserver.model.Architecture;

import java.util.Collection;

/**
 * <p>This model object specifies the parameters of a search into the system for packages.  See the
 * {@link org.haikuos.haikudepotserver.services.SearchPkgsService} for further detail on this.</p>
 */

public class SearchPkgsSpecification {

    public enum ExpressionType {
        CONTAINS
    }

    private String expression;

    private Collection<Architecture> architectures;

    private ExpressionType expressionType;

    private int offset;

    private int limit;

    public int getOffset() {
        return offset;
    }

    public void setOffset(int offset) {
        this.offset = offset;
    }

    public int getLimit() {
        return limit;
    }

    public void setLimit(int value) {
        this.limit = value;
    }

    public String getExpression() {
        return expression;
    }

    public void setExpression(String expression) {
        this.expression = expression;
    }

    public ExpressionType getExpressionType() {
        return expressionType;
    }

    public void setExpressionType(ExpressionType expressionType) {
        this.expressionType = expressionType;
    }

    public Collection<Architecture> getArchitectures() {
        return architectures;
    }

    public void setArchitectures(Collection<Architecture> architectures) {
        this.architectures = architectures;
    }
}
