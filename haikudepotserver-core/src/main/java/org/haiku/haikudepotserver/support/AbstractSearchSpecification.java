/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import java.util.regex.Pattern;

/**
 * <p>Various services are able to perform a search function to return lists of objects.  In order to specify the
 * search, those services would use an abstract subclass of this class.  This class provides the basic search
 * parameters that are re-used.</p>
 */

public abstract class AbstractSearchSpecification {

    public enum ExpressionType {
        CONTAINS
    }

    private String expression;

    private ExpressionType expressionType;

    /**
     * <p>This is the offset into the list of results.</p>
     */

    private int offset = 0;

    /**
     * <p>This value will constrain the quantity of results to this limit.</p>
     */

    private int limit;

    /**
     * <p>Some data can contain active and inactive data.  By default, the results will not contain inactive data.</p>
     */

    private boolean includeInactive = false;

    public boolean getIncludeInactive() {
        return includeInactive;
    }

    public void setIncludeInactive(boolean includeInactive) {
        this.includeInactive = includeInactive;
    }

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

    /**
     * <p>Returns the expression as a regular expression that can be interpreted with the {@link Pattern}
     * class.</p>
     */
    public Pattern getExpressionAsPattern() {
        if (null == getExpression()) {
            return null;
        }

        return switch (getExpressionType()) {
            case CONTAINS -> Pattern.compile(".*" + Pattern.quote(getExpression()) + ".*");
        };
    }

    /**
     * <p>This converts the expression into a form that can be used with a SQL like.</p>
     */

    @SuppressWarnings("unused") // used in Cayenne SQL template.
    public String getExpressionAsSqlLike() {
        if (null == getExpression()) {
            return null;
        }

        switch (getExpressionType()) {
            case CONTAINS:
                return "%" + LikeHelper.ESCAPER.escape(getExpression()) + "%";

            default:
                throw new IllegalStateException("unknown expression type; " + getExpressionType().name());
        }

    }

}
