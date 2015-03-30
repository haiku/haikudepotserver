/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.dataobjects;

import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.auto._PkgVersionLocalization;
import org.haikuos.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haikuos.haikudepotserver.support.cayenne.ExpressionHelper;

import java.util.List;

public class PkgVersionLocalization extends _PkgVersionLocalization implements CreateAndModifyTimestamped {

    public static List<PkgVersionLocalization> getForPkgVersion(ObjectContext context, PkgVersion pkgVersion) {
        Preconditions.checkArgument(null!=context, "the context must be supplied");
        Preconditions.checkArgument(null!=pkgVersion, "the pkg version must be supplied");

        SelectQuery query = new SelectQuery(
                PkgVersionLocalization.class,
                ExpressionFactory.matchExp(PkgVersionLocalization.PKG_VERSION_PROPERTY, pkgVersion));

        query.setCacheGroups(HaikuDepot.CacheGroup.PKG_VERSION_LOCALIZATION.name());
        query.setCacheStrategy(QueryCacheStrategy.SHARED_CACHE);

        return (List<PkgVersionLocalization>) context.performQuery(query);
    }

    public static Optional<PkgVersionLocalization> getForPkgVersionAndNaturalLanguageCode(
            ObjectContext context,
            PkgVersion pkgVersion,
            final String naturalLanguageCode) {

        Preconditions.checkArgument(null!=context, "the context must be supplied");
        Preconditions.checkArgument(null!=pkgVersion, "the pkg version must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(naturalLanguageCode), "the natural language code is required");

        return Iterables.tryFind(
                getForPkgVersion(context, pkgVersion),
                new Predicate<PkgVersionLocalization>() {
                    @Override
                    public boolean apply(PkgVersionLocalization input) {
                        return input.getNaturalLanguage().getCode().equals(naturalLanguageCode);
                    }
                }
        );
    }

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

    public Optional<String> getTitle() {
        if(null != getTitleLocalizationContent()) {
            return Optional.of(getTitleLocalizationContent().getContent());
        }

        return Optional.absent();
    }

    public Optional<String> getSummary() {
        if(null != getSummaryLocalizationContent()) {
            return Optional.of(getSummaryLocalizationContent().getContent());
        }

        return Optional.absent();
    }

    public Optional<String> getDescription() {
        if(null != getDescriptionLocalizationContent()) {
            return Optional.of(getDescriptionLocalizationContent().getContent());
        }

        return Optional.absent();
    }

}
