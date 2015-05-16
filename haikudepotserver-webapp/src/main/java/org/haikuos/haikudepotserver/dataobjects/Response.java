/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.auto._Response;
import org.haikuos.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

/**
 * <p>This is part of the captcha system.</p>
 */

public class Response extends _Response {

    public static Optional<Response> getByToken(ObjectContext context, String token) {
        return ((List<Response>) context.performQuery(new SelectQuery(
                        Response.class,
                        ExpressionFactory.matchExp(Response.TOKEN_PROPERTY, token)))).stream().collect(SingleCollector.optional());
    }

}
