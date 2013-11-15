/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.model;

import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.haikuos.haikudepotserver.model.auto._PkgUrlType;
import org.haikuos.haikudepotserver.model.support.Coded;

import java.util.List;

public class PkgUrlType extends _PkgUrlType implements Coded {

    public static Optional<PkgUrlType> getByCode(ObjectContext context, String code) {
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<PkgUrlType>) context.performQuery(new SelectQuery(
                        PkgUrlType.class,
                        ExpressionFactory.matchExp(Architecture.CODE_PROPERTY, code))),
                null));
    }

}
