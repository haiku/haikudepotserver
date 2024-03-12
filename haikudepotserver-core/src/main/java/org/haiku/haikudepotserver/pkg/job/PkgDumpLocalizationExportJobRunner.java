/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgDumpLocalizationExportJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationContentType;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.pkg.model.dumpexport.DumpExportPkgLocalization;
import org.haiku.haikudepotserver.pkg.model.dumpexport.DumpExportPkgLocalizations;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportPkgLocalizationNaturalLanguage;
import org.haiku.haikudepotserver.reference.model.dumpexport.DumpExportReferenceNaturalLanguage;
import org.haiku.haikudepotserver.support.ArchiveInfo;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Stream;
import java.util.zip.GZIPOutputStream;

/**
 * <p>This runner will produce a JSON dump of all of the user-supplied localizations for all packages. Note that
 * any packages that are subordinate are omitted such as those with suffix <code>_x86</code> for example. Even
 * packages with no localizations are listed.</p>
 */

@Component
public class PkgDumpLocalizationExportJobRunner extends AbstractJobRunner<PkgDumpLocalizationExportJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgDumpLocalizationExportJobRunner.class);

    private final static int BATCH_SIZE = 100;

    private final ServerRuntime serverRuntime;
    private final RuntimeInformationService runtimeInformationService;
    private final ObjectMapper objectMapper;
    private final PkgService pkgService;

    public PkgDumpLocalizationExportJobRunner(
            ServerRuntime serverRuntime,
            RuntimeInformationService runtimeInformationService,
            ObjectMapper objectMapper,
            PkgService pkgService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.runtimeInformationService = Preconditions.checkNotNull(runtimeInformationService);
        this.objectMapper = Preconditions.checkNotNull(objectMapper);
        this.pkgService = Preconditions.checkNotNull(pkgService);
    }

    @Override
    public void run(JobService jobService, PkgDumpLocalizationExportJobSpecification specification)
            throws IOException {

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.JSON_UTF_8.toString(),
                JobDataEncoding.GZIP);

        try (
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
                final JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(gzipOutputStream)
        ) {
            jsonGenerator.writeStartObject();
            writeInfo(jsonGenerator);
            writePkgs(jsonGenerator);
            jsonGenerator.writeEndObject();
        }
    }

    private void writePkgs(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeFieldName("items");
        jsonGenerator.writeStartArray();

        final ObjectContext context = serverRuntime.newContext();
        List<String> pkgNames = getAllPkgNames(context);

        LOGGER.info("will dump pkg versions for {} pkgs", pkgNames.size());

        Lists.partition(pkgNames, BATCH_SIZE).forEach((subPkgNames) -> {
            List<Pkg> pkgs = createPkgSelectByNames(subPkgNames).select(context);
            writePkgs(jsonGenerator, pkgs);
        });

        jsonGenerator.writeEndArray();
    }

    private static PrefetchTreeNode createPkgPrefetchTree() {
        PrefetchTreeNode node = Pkg.PKG_SUPPLEMENT.disjoint();
        node.merge(Pkg.PKG_SUPPLEMENT.dot(PkgSupplement.PKG_LOCALIZATIONS).disjoint());
        return node;
    }

    private static ObjectSelect<Pkg> createPkgSelectByNames(Collection<String> pkgNames) {
        return ObjectSelect.query(Pkg.class)
                .where(Pkg.NAME.in(pkgNames))
                .orderBy(Pkg.NAME.asc())
                .prefetch(createPkgPrefetchTree());
    }

    /**
     * <p>This method will pull down the package names that are to be included. Note that multiple packages
     * may be associated with a simple {@link PkgSupplement} and in this case output will only be provided
     * for the main one. However to keep things simple, here we will output all of them.</p>
     */

    private List<String> getAllPkgNames(ObjectContext context) {
        return ObjectSelect.query(Pkg.class)
                .where(Pkg.ACTIVE.isTrue())
                .orderBy(Pkg.NAME.desc())
                .column(Pkg.NAME)
                .select(context);
    }

    private void writePkgs(
            JsonGenerator jsonGenerator,
            List<Pkg> pkgs) {
        pkgs
                .stream()
                .filter(this::shouldWritePkg)
                .map(PkgDumpLocalizationExportJobRunner::createDumpExportPkgLocalizations)
                .forEach(depl -> {
                    try {
                        objectMapper.writeValue(jsonGenerator, depl);
                    } catch (IOException ioe) {
                        throw new UncheckedIOException(ioe);
                    }
                });
    }

    private boolean shouldWritePkg(Pkg pkg) {
        return pkgService.tryGetMainPkgNameForSubordinatePkg(pkg.getName()).isEmpty();
        // ^ If the pkg is subordinate (eg: src, _x86 variant etc...) then it will return
        // a main pkg name and if this is the case then we don't need to worry about
        // localization for it because the localization will be happening on the main pkg.
    }

    private static Optional<DumpExportPkgLocalization> tryCreateDumpExportPkgLocalization(
            PkgLocalizationContentType type,
            PkgLocalization pkgLocalization,
            Supplier<String> contentSupplier) {
        return Optional
                .ofNullable(contentSupplier.get())
                .filter(StringUtils::isNotBlank)
                .map(c -> {
                    DumpExportPkgLocalization result = new DumpExportPkgLocalization();
                    result.setCode(type.name().toLowerCase(Locale.ROOT));
                    result.setCreateTimestamp(pkgLocalization.getCreateTimestamp().getTime());
                    result.setModifyTimestamp(pkgLocalization.getModifyTimestamp().getTime());
                    result.setNaturalLanguage(createDumpExportPkgLocalizationNaturalLanguage(pkgLocalization.getNaturalLanguage()));
                    result.setContent(contentSupplier.get());
                    return result;
                });
    }

    private static DumpExportPkgLocalizationNaturalLanguage createDumpExportPkgLocalizationNaturalLanguage(NaturalLanguage naturalLanguage) {
        DumpExportPkgLocalizationNaturalLanguage result = new DumpExportPkgLocalizationNaturalLanguage();
        result.setScriptCode(naturalLanguage.getScriptCode());
        result.setCountryCode(naturalLanguage.getCountryCode());
        result.setLanguageCode(naturalLanguage.getLanguageCode());
        result.setCode(naturalLanguage.getCode());
        return result;
    }

    private static DumpExportPkgLocalizations createDumpExportPkgLocalizations(Pkg pkg) {
        DumpExportPkgLocalizations result = new DumpExportPkgLocalizations();
        result.setPkgName(pkg.getName());
        result.setLocalizations(
                pkg.getPkgSupplement().getPkgLocalizations()
                        .stream()
                        .sorted(Comparator.comparing(pl -> pl.getNaturalLanguage().getCode()))
                        .flatMap(pl ->
                                Stream.of(
                                        tryCreateDumpExportPkgLocalization(PkgLocalizationContentType.TITLE, pl, pl::getTitle),
                                        tryCreateDumpExportPkgLocalization(PkgLocalizationContentType.SUMMARY, pl, pl::getSummary),
                                        tryCreateDumpExportPkgLocalization(PkgLocalizationContentType.DESCRIPTION, pl, pl::getDescription)
                                )
                        )
                        .filter(Optional::isPresent)
                        .map(Optional::get)
                        .toList());
        return result;
    }

    private void writeInfo(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeFieldName("info");
        objectMapper.writeValue(jsonGenerator, createArchiveInfo());
    }

    private ArchiveInfo createArchiveInfo() {
        return new ArchiveInfo(
                DateTimeHelper.secondAccuracyDatePlusOneSecond(deriveModifyTimestamp()),
                runtimeInformationService.getProjectVersion());
    }

    private Date deriveModifyTimestamp() {
        ObjectContext context = serverRuntime.newContext();
        return ObjectUtils.firstNonNull(
                ObjectSelect
                        .query(PkgLocalization.class)
                        .max(PkgVersion.MODIFY_TIMESTAMP)
                        .sharedCache()
                        .cacheGroup(HaikuDepot.CacheGroup.PKG.name())
                        .selectFirst(context),
                new Date(0L));
    }

}
