/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import org.apache.cayenne.DataRow;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.query.SQLTemplate;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgVersion;
import org.haiku.haikudepotserver.dataobjects.auto._HaikuDepot;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationLookupService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * <p>This implementation is designed to lookup localization entries based on a fixed
 * set of packages; only localization data for those packages will be found.
 * This allows for a reduced quantity of database communications to occur because
 * the data can be cached in advance.</p>
 */

public class FixedPkgLocalizationLookupServiceImpl implements PkgLocalizationLookupService {

    private final Map<ObjectId, ResolvedPkgVersionLocalization> cachedResult;

    private final String naturalLanguageCode;

    public FixedPkgLocalizationLookupServiceImpl(
            ObjectContext context,
            Collection<PkgVersion> pkgVersions,
            NaturalLanguage naturalLanguage) {

        this.naturalLanguageCode = naturalLanguage.getCode();
        this.cachedResult = new HashMap<>();

        if (!pkgVersions.isEmpty()) {
            Set<Long> pkgVersionIds = pkgVersions
                    .stream()
                    .map((pv) -> (Long) pv.getObjectId().getIdSnapshot().get(PkgVersion.ID_PK_COLUMN))
                    .collect(Collectors.toSet());

            SQLTemplate sqlTemplate = (SQLTemplate) context.getEntityResolver()
                    .getQueryDescriptor(_HaikuDepot.PKG_VERSION_LOCALIZATION_RESOLUTION_QUERYNAME).buildQuery();
            SQLTemplate query = (SQLTemplate) sqlTemplate.createQuery(ImmutableMap.of(
                    "naturalLanguageId", naturalLanguage.getObjectId().getIdSnapshot().get(NaturalLanguage.ID_PK_COLUMN),
                    "englishNaturalLanguageId", NaturalLanguage.getEnglish(context).getObjectId().getIdSnapshot().get(NaturalLanguage.ID_PK_COLUMN),
                    "pkgVersionIds", pkgVersionIds));
            query.setFetchingDataRows(true);

            List<DataRow> dataRows = (List<DataRow>) context.performQuery(query);

            dataRows.forEach((dr) -> {
                Long pkgVersionId = (Long) dr.get("pv_id");
                String title = (String) dr.get("title");
                String description = (String) dr.get("description");
                String summary = (String) dr.get("summary");

                cachedResult.put(
                        ObjectId.of(PkgVersion.class.getSimpleName(), PkgVersion.ID_PK_COLUMN, pkgVersionId),
                        new ResolvedPkgVersionLocalization(title, summary, description));
            });
        }
    }

    @Override
    public ResolvedPkgVersionLocalization resolvePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            Pattern searchPattern,
            NaturalLanguage naturalLanguage) {
        Preconditions.checkNotNull(pkgVersion, "the pkg version must be supplied");
        Preconditions.checkArgument(null == searchPattern, "a search pattern is not supported");
        Preconditions.checkArgument(null != naturalLanguage, "the natural language must be supplied");
        Preconditions.checkArgument(naturalLanguage.getCode().equals(naturalLanguageCode),
                "mismatch between requested and originally specified natural language.");

        ResolvedPkgVersionLocalization result = cachedResult.get(pkgVersion.getObjectId());

        if (null == result) {
            throw new IllegalStateException("the pkg version [" + pkgVersion
                    + "] was queried, but was not in the initial set of package versions supplied");
        }

        return result;
    }
}
