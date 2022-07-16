/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1.support;

/**
 * <p>Some of the API methods call for search functionality.  This typically requires some common query parameters
 * such as the offset into the list and the maximum number of results to return.  This abstract class contains those
 * values for re-use.</p>
 */
@Deprecated
public abstract class AbstractSearchRequest {

    public enum ExpressionType {
        CONTAINS
    }

    public String expression;

    public ExpressionType expressionType;

    /**
     * <P>This is the offset into the result list.</P>
     */

    public Integer offset;

    /**
     * <p>This is the maximum number of results that will be returned by the search.</p>
     */

    public Integer limit;

}
