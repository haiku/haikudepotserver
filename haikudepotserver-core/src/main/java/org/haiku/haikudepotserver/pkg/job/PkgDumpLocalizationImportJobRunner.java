/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgLocalization;
import org.haiku.haikudepotserver.job.AbstractAuthenticatedJobRunner;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoded;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.pkg.model.*;
import org.haiku.haikudepotserver.pkg.model.dumpexport.DumpExportPkgLocalization;
import org.haiku.haikudepotserver.pkg.model.dumpexport.DumpExportPkgLocalizations;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportPkgLocalizationError;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportPkgLocalizationNaturalLanguage;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportPkgLocalizationStatus;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.ArchiveInfo;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.time.Clock;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.zip.GZIPOutputStream;

/**
 * <p>This runner will process a large block of JSON data that is coming in from another source such as Polygot
 * and contains updated localizations for HDS packages.</p>
 */

@Component
public class PkgDumpLocalizationImportJobRunner extends AbstractAuthenticatedJobRunner<PkgDumpLocalizationImportJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgDumpLocalizationImportJobRunner.class);

    private final RuntimeInformationService runtimeInformationService;
    private final ObjectMapper objectMapper;
    private final PkgLocalizationService localizationService;
    private final PkgService pkgService;
    private final PermissionEvaluator permissionEvaluator;

    public PkgDumpLocalizationImportJobRunner(
            ServerRuntime serverRuntime,
            RuntimeInformationService runtimeInformationService,
            ObjectMapper objectMapper,
            PkgService pkgService,
            PkgLocalizationService localizationService,
            PermissionEvaluator permissionEvaluator) {
        super(serverRuntime);
        this.runtimeInformationService = Preconditions.checkNotNull(runtimeInformationService);
        this.objectMapper = Preconditions.checkNotNull(objectMapper);
        this.localizationService = Preconditions.checkNotNull(localizationService);
        this.pkgService = Preconditions.checkNotNull(pkgService);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
    }

    @Override
    public Class<PkgDumpLocalizationImportJobSpecification> getSupportedSpecificationClass() {
        return PkgDumpLocalizationImportJobSpecification.class;
    }

    @Override
    public void runPossiblyAuthenticated(
            JobService jobService,
            PkgDumpLocalizationImportJobSpecification specification) throws IOException, JobRunnerException {
        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != specification.getInputDataGuid(), "missing imput data guid on specification");
        Preconditions.checkArgument(null != specification.getOwnerUserNickname(), "the owner user must be identified");
        Preconditions.checkArgument(null != specification.getOriginSystemDescription(), "the origin system description");

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.JSON_UTF_8.toString(),
                JobDataEncoding.GZIP);

        // if there is input data then feed it in and process it to manipulate the packages'
        // categories.

        JobDataWithByteSource jobDataWithByteSource = jobService.tryObtainData(specification.getInputDataGuid())
                .orElseThrow(() -> new IllegalStateException("the job data was not able to be found for guid; " + specification.getInputDataGuid()));

        try (
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
                final JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(gzipOutputStream)
        ) {
            jsonGenerator.writeStartObject();
            writeInfo(jsonGenerator);

            try (
                    final InputStream inputStream = jobDataWithByteSource.getByteSource().openBufferedStream();
                    final JsonParser jsonParser = objectMapper.getFactory().createParser(inputStream)
            ) {
                importAndWritePkgs(specification, jsonParser, jsonGenerator);
            }

            jsonGenerator.writeEndObject();
        }

    }

    private void importAndWritePkgs(
            PkgDumpLocalizationImportJobSpecification specification,
            JsonParser jsonParser,
            JsonGenerator jsonGenerator) throws IOException, JobRunnerException {

        if (JsonToken.START_OBJECT != jsonParser.nextToken()) {
            throw new JobRunnerException("expected the payload to begin with [" + JsonToken.START_OBJECT + "] but was [" + jsonParser.currentToken() + "]");
        }

        while (true) {
            switch (jsonParser.nextToken()) {
                case END_OBJECT:
                    // finished all the top level key-value pairs
                    return;
                case FIELD_NAME:
                    switch (jsonParser.currentName()) {
                        case "items":
                            if (JsonToken.START_ARRAY != jsonParser.nextToken()) {
                                throw new JobRunnerException("expected the `items` value to begin with [" + JsonToken.START_ARRAY + "]");
                            }

                            jsonGenerator.writeFieldName("items");
                            jsonGenerator.writeStartArray();

                            LOGGER.info("will process the `items`");
                            importAndWritePkgsFromItems(specification, jsonParser, jsonGenerator);
                            LOGGER.info("did process the `items`");

                            jsonGenerator.writeEndArray();

                            break;
                        case "info":
                            if (JsonToken.START_OBJECT != jsonParser.nextToken()) {
                                throw new JobRunnerException("expected the `info` value to begin with [" + JsonToken.START_OBJECT + "]");
                            }
                            LOGGER.info("skipping the `info` object");
                            jsonParser.skipChildren();
                            // ^ no need to read this structure.
                            break;
                        default:
                            throw new JobRunnerException("unexpected top level field [" + jsonParser.currentName() + "]");
                    }
                    break;
                default:
                    throw new JobRunnerException("unexpected top level token [" + jsonParser.getCurrentToken() + "]");
            }
        }
    }

    /**
     * <p>This method expects the parser to be at the start of the <code>items</code> object.</p>
     */
    private void importAndWritePkgsFromItems(
            PkgDumpLocalizationImportJobSpecification specification,
            JsonParser jsonParser,
            JsonGenerator jsonGenerator) throws IOException, JobRunnerException {

        Preconditions.checkState(JsonToken.START_ARRAY == jsonParser.getCurrentToken());

        while (true) {
            switch (jsonParser.nextToken()) {
                case JsonToken.END_ARRAY:
                    // finished all the items
                    return;
                case JsonToken.START_OBJECT:
                    DumpExportPkgLocalizations importItem = jsonParser.readValueAs(DumpExportPkgLocalizations.class);
                    DumpExportPkgLocalizations writeItem = importFromItem(specification, importItem);
                    jsonGenerator.writeObject(writeItem);
                    break;
                default:
                    throw new JobRunnerException("unexpected `items` top level token [" + jsonParser.getCurrentToken() + "]");
            }
        }

    }

    /**
     * <p>This method will process the item and will then return the same data structure back to be serialized into the
     * output.</p>
     *
     * <p>This will operate in a transaction so the whole record will be written or none of the record.</p>
     */
    private DumpExportPkgLocalizations importFromItem(
            PkgDumpLocalizationImportJobSpecification specification,
            DumpExportPkgLocalizations item) {

        Preconditions.checkArgument(null != item, "the item must be supplied");

        DumpExportPkgLocalizations result = createResultForItem(item);
        ObjectContext context = serverRuntime.newContext();

        try {
            Pkg pkg = Optional.of(item.getPkgName())
                    .map(StringUtils::trimToNull)
                    .flatMap(pn -> Pkg.tryGetByName(context, pn))
                    .orElse(null);

            LOGGER.info("will process data for pkg [{}]", pkg);

            if (null == pkg) {
                throw new PkgNotFoundException("the pkg [" + item.getPkgName() + "] was unable to be found");
            }

            if (!shouldImportPkg(pkg)) {
                throw new FailedImportException("the pkg [" + item.getPkgName() + "] is unable to have localizations be imported");
            }

            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    pkg, Permission.PKG_EDITLOCALIZATION)) {
                throw new FailedImportException("unauthorized to edit the localization for [" + pkg + "]");
            }

            List<NaturalLanguageCoded> naturalLanguagesInItem = deriveUniqueNaturalLanguageCoordinates(item);
            List<String> userDescriptionsInItem = deriveUniqueUserDescriptions(item);

            LOGGER.info("did find {} natural languages and {} user description(s) to process", naturalLanguagesInItem.size(), userDescriptionsInItem.size());

            for (NaturalLanguageCoded naturalLanguageInItem : naturalLanguagesInItem) {
                // this is where we start from
                CombinedLocalizationUpdates existing = deriveExistingLocalizationsForPkg(pkg, naturalLanguageInItem);
                // this is where we want to get to
                CombinedLocalizationUpdates finalState =
                        deriveImportOverExistingCombinedLocalizationsNaturalLanguage(existing, naturalLanguageInItem, item);

                // now go through all the authors getting to the final state applying the changes from each author
                // to the data. This will mean there is a distinct change stored for each author.

                for (String userDescription : userDescriptionsInItem) {
                    CombinedLocalizationUpdates nextExisting = finalState.overrideByUserDescription(existing, userDescription);

                    // if the next existing is the same then there is no change.

                    if (!nextExisting.equals(existing)) {
                        localizationService.updatePkgLocalization(
                                context,
                                new NonUserPkgSupplementModificationAgent(
                                        String.format("%s (auth:%s)", userDescription, specification.getOwnerUserNickname()),
                                        specification.getOriginSystemDescription()),
                                pkg.getPkgSupplement(),
                                naturalLanguageInItem,
                                nextExisting.title().content(),
                                nextExisting.summary().content(),
                                nextExisting.description().content());
                        existing = nextExisting;
                    }
                }
            }

            if (context.hasChanges()) {
                context.commitChanges();
                result.setStatus(DumpExportPkgLocalizationStatus.UPDATED);
            }
            else {
                result.setStatus(DumpExportPkgLocalizationStatus.UNCHANGED);
            }

        } catch (PkgNotFoundException pnfe) {
            LOGGER.error("unable to find the package [{}]", item.getPkgName());
            result.setStatus(DumpExportPkgLocalizationStatus.NOTFOUND);
            result.setError(createErrorWithMessage(String.format("unable to find the package [%s]", item.getPkgName())));
        } catch (FailedImportException fie) {
            LOGGER.error("a problem has arisen importing an item from the data", fie);
            result.setStatus(DumpExportPkgLocalizationStatus.ERROR);
            result.setError(createErrorWithMessage(fie.getMessage()));
        }

        return result;
    }

    private void writeInfo(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeFieldName("info");
        objectMapper.writeValue(jsonGenerator, createArchiveInfo());
    }

    private ArchiveInfo createArchiveInfo() {
        return new ArchiveInfo(
                DateTimeHelper.secondAccuracyDatePlusOneSecond(new Date(Clock.systemUTC().millis())),
                // ^ the actual value is meaningless so just adding something for symetry with the export
                runtimeInformationService.getProjectVersion());
    }

    private List<String> deriveUniqueUserDescriptions(DumpExportPkgLocalizations item) {
        return item.getLocalizations()
                .stream().map(DumpExportPkgLocalization::getUserDescription)
                .map(StringUtils::trimToNull)
                .map(ud -> Optional.ofNullable(ud).orElse("?"))
                .distinct()
                .sorted()
                .toList();
    }

    private List<NaturalLanguageCoded> deriveUniqueNaturalLanguageCoordinates(DumpExportPkgLocalizations item) {
        return item.getLocalizations()
                .stream()
                .map(DumpExportPkgLocalization::getNaturalLanguage)
                .map(PkgDumpLocalizationImportJobRunner::toNaturalLanguageCoded)
                .distinct()
                .sorted()
                .toList();
    }

    private static NaturalLanguageCoded toNaturalLanguageCoded(DumpExportPkgLocalizationNaturalLanguage importNaturalLanguage) {
        return new NaturalLanguageCoordinates(
                importNaturalLanguage.getLanguageCode(),
                importNaturalLanguage.getScriptCode(),
                importNaturalLanguage.getCountryCode());
    }

    private DumpExportPkgLocalizations createResultForItem(DumpExportPkgLocalizations item) {
        DumpExportPkgLocalizations result = new DumpExportPkgLocalizations();
        result.setPkgName(item.getPkgName());
        result.setLocalizations(List.of());
        return result;
    }

    private boolean shouldImportPkg(Pkg pkg) {
        return pkgService.tryGetMainPkgNameForSubordinatePkg(pkg.getName()).isEmpty();
        // ^ If the pkg is subordinate (eg: src, _x86 variant etc...) then it will return
        // a main pkg name and if this is the case then we don't need to worry about
        // localization for it because the localization will be happening on the main pkg.
    }

    private CombinedLocalizationUpdates deriveImportOverExistingCombinedLocalizationsNaturalLanguage(
            CombinedLocalizationUpdates existing,
            NaturalLanguageCoded naturalLanguage,
            DumpExportPkgLocalizations item) throws FailedImportException {
        CombinedLocalizationUpdates result = existing;
        List<DumpExportPkgLocalization> importLocalizationsForNaturalLanguage = item.getLocalizations().stream()
                .filter(l -> 0 == NaturalLanguageCoded.NATURAL_LANGUAGE_CODE_COMPARATOR.compare(
                        toNaturalLanguageCoded(l.getNaturalLanguage()), naturalLanguage))
                .toList();

        for (DumpExportPkgLocalization importLocalization : importLocalizationsForNaturalLanguage) {
            result = switch (PkgLocalizationContentType
                    .tryFromCode(importLocalization.getCode())
                    .orElseThrow(() -> new FailedImportException("unexpected code [" + importLocalization.getCode() + "]"))) {
                case PkgLocalizationContentType.TITLE ->
                        StringUtils.equals(StringUtils.trimToNull(importLocalization.getContent()), result.title().content())
                                ? result :
                                new CombinedLocalizationUpdates(
                                        new LocalizationUpdate(
                                                StringUtils.trimToNull(importLocalization.getContent()),
                                                StringUtils.trimToNull(importLocalization.getUserDescription())),
                                        result.summary(),
                                        result.description()
                                );
                case PkgLocalizationContentType.SUMMARY ->
                        StringUtils.equals(StringUtils.trimToNull(importLocalization.getContent()), result.summary().content())
                                ? result :
                                new CombinedLocalizationUpdates(
                                        result.title(),
                                        new LocalizationUpdate(
                                                StringUtils.trimToNull(importLocalization.getContent()),
                                                StringUtils.trimToNull(importLocalization.getUserDescription())),
                                        result.description()
                                );
                case PkgLocalizationContentType.DESCRIPTION ->
                        StringUtils.equals(StringUtils.trimToNull(importLocalization.getContent()), result.description().content())
                                ? result :
                                new CombinedLocalizationUpdates(
                                        result.title(),
                                        result.summary(),
                                        new LocalizationUpdate(
                                                StringUtils.trimToNull(importLocalization.getContent()),
                                                StringUtils.trimToNull(importLocalization.getUserDescription()))
                                );
            };
        }

        return result;
    }

    private CombinedLocalizationUpdates deriveExistingLocalizationsForPkg(Pkg pkg, NaturalLanguageCoded naturalLanguage) {
        Optional<PkgLocalization> pkgLocalizationOptional = pkg.getPkgSupplement().getPkgLocalization(naturalLanguage);
        return new CombinedLocalizationUpdates(
                new LocalizationUpdate(
                        pkgLocalizationOptional.map(PkgLocalization::getTitle).map(StringUtils::trimToNull).orElse(null),
                        null
                ),
                new LocalizationUpdate(
                        pkgLocalizationOptional.map(PkgLocalization::getSummary).map(StringUtils::trimToNull).orElse(null),
                        null
                ),
                new LocalizationUpdate(
                        pkgLocalizationOptional.map(PkgLocalization::getDescription).map(StringUtils::trimToNull).orElse(null),
                        null)
        );
    }

    private static DumpExportPkgLocalizationError createErrorWithMessage(String message) {
        DumpExportPkgLocalizationError error = new DumpExportPkgLocalizationError();
        error.setMessage(message);
        return error;
    }

    private record LocalizationUpdate(String content, String userDescription) {}

    private record CombinedLocalizationUpdates(
            LocalizationUpdate title,
            LocalizationUpdate summary,
            LocalizationUpdate description) {

        /**
         * <p>This will return a new {@link CombinedLocalizationUpdates} with the values from the supplied base
         * overridden by values from this {@link CombinedLocalizationUpdates}, but only where the userDescription
         * matches.</p>
         */
        private CombinedLocalizationUpdates overrideByUserDescription(
                CombinedLocalizationUpdates base,
                String userDescription) {
            return new CombinedLocalizationUpdates(
                    StringUtils.equals(userDescription, title().userDescription()) ? title() : base.title(),
                    StringUtils.equals(userDescription, summary().userDescription()) ? summary() : base.summary(),
                    StringUtils.equals(userDescription, description().userDescription()) ? description() : base.description());
        }

    }

    private static final class PkgNotFoundException extends Exception {
        public PkgNotFoundException(String message) {
            super(message);
        }
    }

    private static final class FailedImportException extends Exception {
        public FailedImportException(String message) {
            super(message);
        }
    }

}
