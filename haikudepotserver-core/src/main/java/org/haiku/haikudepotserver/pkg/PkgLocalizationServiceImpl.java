/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PkgLocalizationServiceImpl implements PkgLocalizationService {

    private void fill(ResolvedPkgVersionLocalization result, Pattern pattern, PkgVersionLocalization pvl) {
        if(Strings.isNullOrEmpty(result.getTitle())
                && !Strings.isNullOrEmpty(pvl.getTitle().orElse(null))
                && (null==pattern || pattern.matcher(pvl.getTitle().get()).matches())) {
            result.setTitle(pvl.getTitle().get());
        }

        if(Strings.isNullOrEmpty(result.getSummary())
                && !Strings.isNullOrEmpty(pvl.getSummary().orElse(null))
                && (null==pattern || pattern.matcher(pvl.getSummary().get()).matches()) ) {
            result.setSummary(pvl.getSummary().orElse(null));
        }

        if(Strings.isNullOrEmpty(result.getDescription())
                && !Strings.isNullOrEmpty(pvl.getDescription().orElse(null))
                && (null==pattern || pattern.matcher(pvl.getDescription().get()).matches()) ) {
            result.setDescription(pvl.getDescription().orElse(null));
        }
    }

    private void fill(ResolvedPkgVersionLocalization result, Pattern pattern, PkgLocalization pl) {
        if(Strings.isNullOrEmpty(result.getTitle())
                && !Strings.isNullOrEmpty(pl.getTitle())
                && (null==pattern || pattern.matcher(pl.getTitle()).matches())) {
            result.setTitle(pl.getTitle());
        }

        if(Strings.isNullOrEmpty(result.getSummary())
                && !Strings.isNullOrEmpty(pl.getSummary())
                && (null==pattern || pattern.matcher(pl.getSummary()).matches())) {
            result.setSummary(pl.getSummary());
        }

        if(Strings.isNullOrEmpty(result.getDescription())
                && !Strings.isNullOrEmpty(pl.getDescription())
                && (null==pattern || pattern.matcher(pl.getDescription()).matches())) {
            result.setDescription(pl.getDescription());
        }
    }

    private void fillResolvedPkgVersionLocalization(
            ResolvedPkgVersionLocalization result,
            ObjectContext context,
            PkgVersion pkgVersion,
            Pattern searchPattern,
            NaturalLanguage naturalLanguage) {

        if(!result.hasAll()) {
            PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                context, pkgVersion, naturalLanguage.getCode())
                    .ifPresent(pvlNl -> fill(result, searchPattern, pvlNl));
        }

        if(!result.hasAll()) {
            PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    naturalLanguage.getCode())
                    .ifPresent(plNl -> fill(result, searchPattern, plNl));
        }

        if(!result.hasAll()) {
            PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, NaturalLanguage.CODE_ENGLISH)
                    .ifPresent(pvlEn -> fill(result, searchPattern, pvlEn));
        }

        if(!result.hasAll()) {
            PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    NaturalLanguage.CODE_ENGLISH)
                    .ifPresent(plEn -> fill(result, searchPattern, plEn));
        }

        if(null!=searchPattern) {
            fillResolvedPkgVersionLocalization(result, context, pkgVersion, null, naturalLanguage);
        }
    }

    @Override
    public ResolvedPkgVersionLocalization resolvePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            Pattern searchPattern,
            NaturalLanguage naturalLanguage) {
        ResolvedPkgVersionLocalization result = new ResolvedPkgVersionLocalization();
        fillResolvedPkgVersionLocalization(result,context,pkgVersion,searchPattern,naturalLanguage);
        return result;
    }

    @Override
    public PkgLocalization updatePkgLocalization(
            ObjectContext context,
            PkgSupplement pkgSupplement,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null != pkgSupplement, "the pkg supplement must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the naturallanguage must be provided");

        return updatePkgLocalizationWithoutSideEffects(
                context, pkgSupplement, naturalLanguage,
                title, summary, description);
    }

    private PkgLocalization updatePkgLocalizationWithoutSideEffects(
            ObjectContext context,
            PkgSupplement pkgSupplement,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null != pkgSupplement, "the pkg supplement must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the naturallanguage must be provided");

        title = StringUtils.trimToNull(title);
        summary = StringUtils.trimToNull(summary);
        description = StringUtils.trimToNull(description);

        // was using the static method, but won't work with temporary objects.
        Optional<PkgLocalization> pkgLocalizationOptional = pkgSupplement.getPkgLocalization(naturalLanguage);

        if(Strings.isNullOrEmpty(title) && Strings.isNullOrEmpty(summary) && Strings.isNullOrEmpty(description)) {
            pkgLocalizationOptional.ifPresent((context::deleteObject));
            return null;
        }

        PkgLocalization pkgLocalization = pkgLocalizationOptional.orElseGet(() -> {
            PkgLocalization created = context.newObject(PkgLocalization.class);
            created.setNaturalLanguage(naturalLanguage);
            pkgSupplement.addToManyTarget(PkgSupplement.PKG_LOCALIZATIONS.getName(), created, true);
            return created;
        });

        pkgLocalization.setTitle(title);
        pkgLocalization.setSummary(summary);
        pkgLocalization.setDescription(description);

        pkgSupplement.setModifyTimestamp();

        return pkgLocalization;
    }

    @Override
    public PkgVersionLocalization updatePkgVersionLocalization(
            ObjectContext context,
            PkgVersion pkgVersion,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null != naturalLanguage, "the natural language must be provided");

        title = StringUtils.trimToNull(title);
        summary = StringUtils.trimToNull(summary);
        description = StringUtils.trimToNull(description);

        Set<String> localizedStrings = Arrays.stream(new String[] { title, summary, description })
                .filter((s) -> !Strings.isNullOrEmpty(s))
                .collect(Collectors.toSet());

        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional =
                pkgVersion.getPkgVersionLocalization(naturalLanguage);

        if (localizedStrings.isEmpty()) {
            if(pkgVersionLocalizationOptional.isPresent()) {
                pkgVersion.removeToManyTarget(
                        PkgVersion.PKG_VERSION_LOCALIZATIONS.getName(),
                        pkgVersionLocalizationOptional.get(),
                        true);

                context.deleteObjects(pkgVersionLocalizationOptional.get());
            }

            return null;
        }
        else {

            PkgVersionLocalization pkgVersionLocalization;

            if (!pkgVersionLocalizationOptional.isPresent()) {
                pkgVersionLocalization = context.newObject(PkgVersionLocalization.class);
                pkgVersionLocalization.setNaturalLanguage(naturalLanguage);
                pkgVersion.addToManyTarget(PkgVersion.PKG_VERSION_LOCALIZATIONS.getName(), pkgVersionLocalization, true);
            } else {
                pkgVersionLocalization = pkgVersionLocalizationOptional.get();
            }

            Map<String, LocalizationContent> localizationContentMap = LocalizationContent.getOrCreateLocalizationContents
                    (context, localizedStrings);

            pkgVersionLocalization.setDescriptionLocalizationContent(
                    localizationContentMap.getOrDefault(description, null));

            pkgVersionLocalization.setSummaryLocalizationContent(
                    localizationContentMap.getOrDefault(summary, null));

            pkgVersionLocalization.setTitleLocalizationContent(
                    localizationContentMap.getOrDefault(title, null));

            return pkgVersionLocalization;
        }

    }

}
