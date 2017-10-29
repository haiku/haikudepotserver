/*
 * Copyright 2013-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._Response;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.Optional;

/**
 * <p>This is part of the captcha system.</p>
 */

public class Response extends _Response {

    public static Optional<Response> getByToken(ObjectContext context, String token) {
        return ObjectSelect
                .query(Response.class)
                .where(Response.TOKEN.eq(token))
                .select(context)
                .stream()
                .collect(SingleCollector.optional());
    }

}
