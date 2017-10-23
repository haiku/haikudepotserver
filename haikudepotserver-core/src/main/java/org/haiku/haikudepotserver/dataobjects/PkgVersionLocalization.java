/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.dataobjects;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.auto._PkgVersionLocalization;
import org.haiku.haikudepotserver.dataobjects.support.CreateAndModifyTimestamped;
import org.haiku.haikudepotserver.support.SingleCollector;

import java.util.List;
import java.util.Optional;

public class PkgVersionLocalization extends _PkgVersionLocalization implements CreateAndModifyTimestamped {

    private static List<PkgVersionLocalization> getForPkgVersion(ObjectContext context, PkgVersion pkgVersion) {
        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkgVersion, "the pkg version must be supplied");
        return ObjectSelect
                .query(PkgVersionLocalization.class)
                .where(PKG_VERSION.eq(pkgVersion))
                .sharedCache()
                .cacheGroup(HaikuDepot.CacheGroup.PKG_VERSION_LOCALIZATION.name())
                .select(context);
    }

    public static Optional<PkgVersionLocalization> getForPkgVersionAndNaturalLanguageCode(
            ObjectContext context,
            PkgVersion pkgVersion,
            final String naturalLanguageCode) {

        Preconditions.checkArgument(null != context, "the context must be supplied");
        Preconditions.checkArgument(null != pkgVersion, "the pkg version must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(naturalLanguageCode), "the natural language code is required");
        return getForPkgVersion(context, pkgVersion)
                .stream()
                .filter(pvl -> pvl.getNaturalLanguage().getCode().equals(naturalLanguageCode))
                .collect(SingleCollector.optional());
    }

    public static Optional<PkgVersionLocalization> getAnyPkgVersionLocalizationForPkg(ObjectContext context, Pkg pkg) {

        // the ordering is only important in order to ensure that integration tests are repeatable.

        return Optional.ofNullable(ObjectSelect.query(PkgVersionLocalization.class)
                .where(PKG_VERSION.dot(PkgVersion.PKG).eq(pkg))
                .and(PKG_VERSION.dot(PkgVersion.IS_LATEST).isTrue())
                .and(PKG_VERSION.dot(PkgVersion.ACTIVE).isTrue())
                .orderBy(
                        PKG_VERSION.dot(PkgVersion.ARCHITECTURE).dot(Architecture.CODE).desc(),
                        NATURAL_LANGUAGE.dot(NaturalLanguage.CODE).asc())
                .selectFirst(context));
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
