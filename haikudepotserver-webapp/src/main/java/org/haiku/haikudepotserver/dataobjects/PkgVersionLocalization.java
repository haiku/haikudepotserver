/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.QueryCacheStrategy;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersionLocalization;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.support.cayenne.ExpressionHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
        return getForPkgVersion(context, pkgVersion)
                .stream()
                .filter(pvl -> pvl.getNaturalLanguage().getCode().equals(naturalLanguageCode))
                .collect(SingleCollector.optional());
    }

    public static Optional<PkgVersionLocalization> getAnyPkgVersionLocalizationForPkg(ObjectContext context, Pkg pkg) {

        List<Expression> expressions = new ArrayList<>();
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
                        new Ordering(pvProp + "." + PkgVersion.ARCHITECTURE_PROPERTY, SortOrder.DESCENDING),
                        new Ordering(PkgVersionLocalization.NATURAL_LANGUAGE_PROPERTY + "." + NaturalLanguage.CODE_PROPERTY, SortOrder.ASCENDING)
                ));

        List<PkgVersionLocalization> locs = (List<PkgVersionLocalization>) context.performQuery(query);

        if(locs.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(locs.get(0));
    }

    public Optional<String> getTitle() {
        if(null != getTitleLocalizationContent()) {
            return Optional.of(getTitleLocalizationContent().getContent());
        }

        return Optional.empty();
    }

    public Optional<String> getSummary() {
        if(null != getSummaryLocalizationContent()) {
            return Optional.of(getSummaryLocalizationContent().getContent());
        }

        return Optional.empty();
    }

    public Optional<String> getDescription() {
        if(null != getDescriptionLocalizationContent()) {
            return Optional.of(getDescriptionLocalizationContent().getContent());
        }

        return Optional.empty();
    }

}
