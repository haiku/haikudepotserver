/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.dataobjects.auto._Pkg;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationService;
import org.haiku.haikudepotserver.pkg.model.ResolvedPkgVersionLocalization;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class PkgLocalizationServiceImpl implements PkgLocalizationService {

    @Resource
    private PkgServiceImpl pkgServiceImpl;

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
            Optional<PkgVersionLocalization> pvlNl = PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, naturalLanguage.getCode());

            if (pvlNl.isPresent()) {
                fill(result, searchPattern, pvlNl.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgLocalization> plNl = PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    naturalLanguage.getCode());

            if(plNl.isPresent()) {
                fill(result, searchPattern, plNl.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgVersionLocalization> pvlEn = PkgVersionLocalization.getForPkgVersionAndNaturalLanguageCode(
                    context, pkgVersion, NaturalLanguage.CODE_ENGLISH);

            if(pvlEn.isPresent()) {
                fill(result, searchPattern, pvlEn.get());
            }
        }

        if(!result.hasAll()) {
            Optional<PkgLocalization> plEn = PkgLocalization.getForPkgAndNaturalLanguageCode(
                    context,
                    pkgVersion.getPkg(),
                    NaturalLanguage.CODE_ENGLISH);

            if(plEn.isPresent()) {
                fill(result, searchPattern, plEn.get());
            }
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
            Pkg pkg,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null != pkg, "the package must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the naturallanguage must be provided");

        PkgLocalization result = updatePkgLocalizationWithoutSideEffects(
                context, pkg, naturalLanguage,
                title, summary, description);

        // riding off the back of this, if there is a "_devel" package of the same name
        // then its localization should be configured at the same time.

        pkgServiceImpl.tryGetDevelPkg(context, pkg.getName()).ifPresent((develPkg) -> {
            updatePkgLocalization(
                    context, develPkg, naturalLanguage,
                    title, summary + SUFFIX_SUMMARY_DEVELOPMENT, description);
        });

        return result;
    }

    private PkgLocalization updatePkgLocalizationWithoutSideEffects(
            ObjectContext context,
            Pkg pkg,
            NaturalLanguage naturalLanguage,
            String title,
            String summary,
            String description) {

        Preconditions.checkArgument(null != pkg, "the package must be provided");
        Preconditions.checkArgument(null != naturalLanguage, "the naturallanguage must be provided");

        if(null!=title) {
            title = title.trim();
        }

        if(null!=summary) {
            summary = summary.trim();
        }

        if(null!=description) {
            description = description.trim();
        }

        // was using the static method, but won't work with temporary objects.
        Optional<PkgLocalization> pkgLocalizationOptional = pkg.getPkgLocalization(naturalLanguage);

        if(Strings.isNullOrEmpty(title) && Strings.isNullOrEmpty(summary) && Strings.isNullOrEmpty(description)) {
            if(pkgLocalizationOptional.isPresent()) {
                context.deleteObject(pkgLocalizationOptional.get());
            }

            return null;
        }

        if(!pkgLocalizationOptional.isPresent()) {
            PkgLocalization pkgLocalization = context.newObject(PkgLocalization.class);
            pkgLocalization.setNaturalLanguage(naturalLanguage);
            pkgLocalization.setTitle(title);
            pkgLocalization.setSummary(summary);
            pkgLocalization.setDescription(description);
            pkg.addToManyTarget(_Pkg.PKG_LOCALIZATIONS.getName(), pkgLocalization, true);
            return pkgLocalization;
        }

        PkgLocalization pkgLocalization = pkgLocalizationOptional.get();

        pkgLocalization.setTitle(title);
        pkgLocalization.setSummary(summary);
        pkgLocalization.setDescription(description);

        return pkgLocalization;
    }

    private PkgLocalization replicatePkgLocalizationWithoutSideEffects(
            ObjectContext context,
            PkgLocalization pkgLocalization,
            Pkg targetPkg,
            String summarySuffix) {
        return updatePkgLocalizationWithoutSideEffects(
                context, targetPkg, pkgLocalization.getNaturalLanguage(),
                pkgLocalization.getTitle(), pkgLocalization.getSummary() + (null != summarySuffix ? summarySuffix : ""),
                pkgLocalization.getDescription());
    }

    @Override
    public void replicatePkgLocalizations(
            ObjectContext context,
            Pkg sourcePkg,
            Pkg targetPkg,
            String summarySuffix) {

        // delete any pkg localizations from the target which are not present in the source.

        List<PkgLocalization> pkgLocalizationsToDelete = targetPkg.getPkgLocalizations()
                .stream()
                .filter((spl) -> !sourcePkg.getPkgLocalizations()
                        .stream()
                        .anyMatch((tpl) -> !tpl.getNaturalLanguage().equals(spl.getNaturalLanguage())))
                .collect(Collectors.toList());

        context.deleteObjects(pkgLocalizationsToDelete);

        // now override the target with the source.

        sourcePkg.getPkgLocalizations()
                .forEach((spl) -> replicatePkgLocalizationWithoutSideEffects(context, spl, targetPkg, summarySuffix));
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

        if(null!=title) {
            title = title.trim();
        }

        if(null!=summary) {
            summary = summary.trim();
        }

        if(null!=description) {
            description = description.trim();
        }

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
