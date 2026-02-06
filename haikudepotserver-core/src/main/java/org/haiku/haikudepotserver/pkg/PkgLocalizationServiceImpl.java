/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.PkgSupplementModificationAgent;
import org.haiku.haikudepotserver.pkg.model.PkgSupplementModificationService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PkgLocalizationServiceImpl implements PkgLocalizationService {

    private final PkgSupplementModificationService pkgSupplementModificationService;

    public PkgLocalizationServiceImpl(PkgSupplementModificationService pkgSupplementModificationService) {
        this.pkgSupplementModificationService = Preconditions.checkNotNull(pkgSupplementModificationService);
    }

    private void fill(ResolvedPkgVersionLocalization result, Pattern pattern, PkgVersionLocalization pvl) {
        if(StringUtils.isEmpty(result.getTitle())
                && !StringUtils.isEmpty(pvl.getTitle().orElse(null))
                && (null == pattern || pattern.matcher(pvl.getTitle().get()).matches())) {
            result.setTitle(pvl.getTitle().get());
        }

        if(StringUtils.isEmpty(result.getSummary())
                && !StringUtils.isEmpty(pvl.getSummary().orElse(null))
                && (null == pattern || pattern.matcher(pvl.getSummary().get()).matches()) ) {
            result.setSummary(pvl.getSummary().orElse(null));
        }

        if(StringUtils.isEmpty(result.getDescription())
                && !StringUtils.isEmpty(pvl.getDescription().orElse(null))
                && (null == pattern || pattern.matcher(pvl.getDescription().get()).matches()) ) {
            result.setDescription(pvl.getDescription().orElse(null));
        }
    }

    private void fill(ResolvedPkgVersionLocalization result, Pattern pattern, PkgLocalization pl) {
        if(StringUtils.isEmpty(result.getTitle())
                && !StringUtils.isEmpty(pl.getTitle())
                && (null == pattern || pattern.matcher(pl.getTitle()).matches())) {
            result.setTitle(pl.getTitle());
        }

        if(StringUtils.isEmpty(result.getSummary())
                && !StringUtils.isEmpty(pl.getSummary())
                && (null == pattern || pattern.matcher(pl.getSummary()).matches())) {
            result.setSummary(pl.getSummary());
        }

        if(StringUtils.isEmpty(result.getDescription())
                && !StringUtils.isEmpty(pl.getDescription())
                && (null == pattern || pattern.matcher(pl.getDescription()).matches())) {
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
            PkgLocalization.tryGetForPkgAndNaturalLanguage(
                    context,
                    pkgVersion.getPkg(),
                    naturalLanguage)
                    .ifPresent(plNl -> fill(result, searchPattern, plNl));
        }

        if(!result.hasAll()) {
            PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, NaturalLanguageCoordinates.LANGUAGE_CODE_ENGLISH)
                    .ifPresent(pvlEn -> fill(result, searchPattern, pvlEn));
        }

        if(!result.hasAll()) {
            PkgLocalization.tryGetForPkgAndNaturalLanguage(
                    context,
                    pkgVersion.getPkg(),
                    NaturalLanguageCoordinates.english())
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
        fillResolvedPkgVersionLocalization(result, context, pkgVersion, searchPattern, naturalLanguage);
        return result;
    }

    @Override
    public PkgLocalization updatePkgLocalization(
            ObjectContext context,
            PkgSupplementModificationAgent agent,
            PkgSupplement pkgSupplement,
            NaturalLanguageCoded naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null != pkgSupplement, "the pkg supplement must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the naturallanguage must be provided");
        Preconditions.checkArgument(null != agent, "the agent must be provided");

        return updatePkgLocalizationWithoutSideEffects(
                context, agent, pkgSupplement, naturalLanguage,
                title, summary, description);
    }

    private PkgLocalization updatePkgLocalizationWithoutSideEffects(
            ObjectContext context,
            PkgSupplementModificationAgent agent,
            PkgSupplement pkgSupplement,
            NaturalLanguageCoded naturalLanguage,
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

        if(StringUtils.isEmpty(title) && StringUtils.isEmpty(summary) && StringUtils.isEmpty(description)) {
            pkgLocalizationOptional.ifPresent((context::deleteObject));
            return null;
        }

        PkgLocalization pkgLocalization = pkgLocalizationOptional.orElseGet(() -> {
            PkgLocalization created = context.newObject(PkgLocalization.class);
            created.setNaturalLanguage(NaturalLanguage.getByNaturalLanguage(context, naturalLanguage));
            pkgSupplement.addToManyTarget(PkgSupplement.PKG_LOCALIZATIONS.getName(), created, true);
            return created;
        });

        boolean titleChanged = !Strings.CS.equals(title, pkgLocalization.getTitle());
        boolean summaryChanged = !Strings.CS.equals(summary, pkgLocalization.getSummary());
        boolean descriptionChanged = !Strings.CS.equals(description, pkgLocalization.getDescription());

        if (titleChanged || summaryChanged || descriptionChanged) {
            String result = String.format("changing localization for pkg [%s] in natural language [%s];",
                    pkgSupplement.getBasePkgName(), NaturalLanguageCoordinates.fromCoded(naturalLanguage)) +
                    createPkgSupplicantLocalizationElementChange("title", pkgLocalization.getTitle(), title) +
                    createPkgSupplicantLocalizationElementChange("summary", pkgLocalization.getSummary(), summary) +
                    createPkgSupplicantLocalizationElementChange("description", pkgLocalization.getDescription(), description);

            pkgSupplementModificationService.appendModification(context, pkgSupplement, agent, result);
        }

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
            NaturalLanguageCoded naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null != naturalLanguage, "the natural language must be provided");

        title = StringUtils.trimToNull(title);
        summary = StringUtils.trimToNull(summary);
        description = StringUtils.trimToNull(description);

        Set<String> localizedStrings = Arrays.stream(new String[] { title, summary, description })
                .filter((s) -> !StringUtils.isEmpty(s))
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

            if (pkgVersionLocalizationOptional.isEmpty()) {
                pkgVersionLocalization = context.newObject(PkgVersionLocalization.class);
                pkgVersionLocalization.setNaturalLanguage(NaturalLanguage.getByNaturalLanguage(context, naturalLanguage));
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

    private String createPkgSupplicantLocalizationElementChange(
            String elementName,
            String existingContent,
            String newContent) {
        if (!Strings.CS.equals(existingContent, newContent)) {
            if (StringUtils.isEmpty(newContent)) {
                return String.format("\n%s: deleted", elementName);
            }
            return String.format("\n%s: [%s]", elementName, StringUtils.abbreviateMiddle(newContent, "...", 80));
        }
        return "";
    }

}
