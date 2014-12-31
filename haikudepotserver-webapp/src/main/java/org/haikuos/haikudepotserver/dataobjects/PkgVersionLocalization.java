/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgVersionLocalization;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;

import java.util.List;

public class PkgVersionLocalization extends _PkgVersionLocalization implements CreateAndModifyTimestamped {

    public static Optional<PkgVersionLocalization> getAnyPkgVersionLocalizationForPkg(ObjectContext context, Pkg pkg) {

        List<Expression> expressions = Lists.newArrayList();
        String pvProp = PkgVersionLocalization.PKG_VERSION_PROPERTY;

        expressions.add(ExpressionFactory.matchExp(
                pvProp + "." + PkgVersion.PKG_PROPERTY + ".",
                pkg));

        expressions.add(ExpressionFactory.matchExp(
                pvProp + "." + PkgVersion.IS_LATEST_PROPERTY,
                true));

        expressions.add(ExpressionFactory.matchExp(
                pvProp + "." + PkgVersion.ACTIVE_PROPERTY,
                true));

        // the ordering is only important in order to ensure that integration tests are repeatable.

        SelectQuery query = new SelectQuery(
                PkgVersionLocalization.class,
                ExpressionHelper.andAll(expressions),
                ImmutableList.of(
                        new Ordering(pvProp + "." + PkgVersion.IS_LATEST_PROPERTY,SortOrder.DESCENDING),
                        new Ordering(pvProp + "." + PkgVersion.ARCHITECTURE_PROPERTY, SortOrder.DESCENDING)
                ));

        List<PkgVersionLocalization> locs = (List<PkgVersionLocalization>) context.performQuery(query);

        if(locs.isEmpty()) {
            return Optional.absent();
        }

        return Optional.of(locs.get(0));
    }

    public boolean equalsForContent(PkgVersionLocalization other) {
        return
                getSummary().equals(other.getSummary())
                && getDescription().equals(other.getDescription());
    }

}
