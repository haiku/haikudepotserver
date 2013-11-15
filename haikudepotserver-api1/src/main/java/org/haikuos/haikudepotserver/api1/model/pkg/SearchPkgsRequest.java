/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.pkg;

/**
 * <p>This is the model object that is used to define the request to search for packages in the system.</p>
 */

public class SearchPkgsRequest {

    public enum ExpressionType {
        CONTAINS
    }

    public String expression;

    public String architectureCode;

    public ExpressionType expressionType;

    public Integer offset;

    public Integer limit;


}
