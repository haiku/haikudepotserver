/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgCategory;
import org.haikuos.haikudepotserver.dataobjects.support.Coded;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PkgCategory extends _PkgCategory implements Coded {

    public static List<PkgCategory> getAll(ObjectContext context) {
        Preconditions.checkNotNull(context);
        SelectQuery query = new SelectQuery(PkgCategory.class);
        query.addOrdering(new Ordering(NAME_PROPERTY, SortOrder.ASCENDING));
        return (List<PkgCategory>) context.performQuery(query);
    }

    public static Optional<PkgCategory> getByCode(ObjectContext context, String code) {
        Preconditions.checkNotNull(context);
        Preconditions.checkState(!Strings.isNullOrEmpty(code));
        return Optional.fromNullable(Iterables.getOnlyElement(
                (List<PkgCategory>) context.performQuery(new SelectQuery(
                        PkgCategory.class,
                        ExpressionFactory.matchExp(PkgCategory.CODE_PROPERTY, code))),
                null));
    }

    /**
     * <p>Given the codes supplied, this method will return all of the categories for
     * those codes.</p>
     */

    public static List<PkgCategory> getByCodes(ObjectContext context, Collection<String> codes) {
        Preconditions.checkNotNull(context);
        Preconditions.checkNotNull(codes);

        if(codes.isEmpty()) {
            return Collections.emptyList();
        }

        Expression codeExpression = null;

        for(String code : codes) {
            if(null==codeExpression) {
                codeExpression = ExpressionFactory.matchExp(PkgCategory.CODE_PROPERTY, code);
            }
            else {
                codeExpression = codeExpression.orExp(ExpressionFactory.matchExp(PkgCategory.CODE_PROPERTY, code));
            }
        }

        return (List<PkgCategory>) context.performQuery(new SelectQuery(PkgCategory.class, codeExpression));
    }

    /**
     * <p>Can be looked up in the localizations to get a title for this category.</p>
     */

    public String getTitleKey() {
        return "pkgCategory." + getCode().toLowerCase() + ".title";
    }

}
