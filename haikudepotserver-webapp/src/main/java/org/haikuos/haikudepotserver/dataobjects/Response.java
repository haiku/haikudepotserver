/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.dataobjects.auto._Response;

import java.util.List;

/**
 * <p>This is part of the captcha system.</p>
 */

public class Response extends _Response {

    public static Optional<Response> getByToken(ObjectContext context, String token) {
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<Response>) context.performQuery(new SelectQuery(
                        Response.class,
                        ExpressionFactory.matchExp(Response.TOKEN_PROPERTY, token))),
                null));
    }

}
