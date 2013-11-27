/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.model;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.model.auto._MediaType;

import java.util.List;

public class MediaType extends _MediaType {

    public static Optional<MediaType> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<MediaType>) context.performQuery(new SelectQuery(
                        MediaType.class,
                        ExpressionFactory.matchExp(MediaType.CODE_PROPERTY, code))),
                null));
    }

}
