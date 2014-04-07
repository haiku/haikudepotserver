/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.auto._Architecture;

import java.util.List;

public class Architecture extends _Architecture {

    public final static String CODE_SOURCE = "source";
    public final static String CODE_ANY = "any";

    public static List<Architecture> getAll(ObjectContext context) {
        SelectQuery query = new SelectQuery(Architecture.class);
        query.addOrdering(Architecture.CODE_PROPERTY, SortOrder.ASCENDING);
        return (List<Architecture>) context.performQuery(query);
    }

    public static Optional<Architecture> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<Architecture>) context.performQuery(new SelectQuery(
                        Architecture.class,
                        ExpressionFactory.matchExp(Architecture.CODE_PROPERTY, code))),
                null));
    }

    @Override
    public String toString() {
        return "arch;"+getCode();
    }

}
