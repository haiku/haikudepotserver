/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.job;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Country;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.UserRatingStability;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.job.PkgDumpExportJobRunner;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.haiku.haikudepotserver.reference.model.dumpexport.*;
import org.haiku.haikudepotserver.support.ArchiveInfo;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.cayenne.GeneralQueryHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * <p>This produces a set of reference data that can be used by the HaikuDepot
 * client to get a list of valid categories, natural languages, etc...</p>
 */

@Component
public class ReferenceDumpExportJobRunner extends AbstractJobRunner<ReferenceDumpExportJobSpecification> {

    protected static final Logger LOGGER = LoggerFactory.getLogger(PkgDumpExportJobRunner.class);

    private final ServerRuntime serverRuntime;
    private final MessageSource messageSource;
    private final RuntimeInformationService runtimeInformationService;

    private final NaturalLanguageService naturalLanguageService;

    private final ObjectMapper objectMapper;

    public ReferenceDumpExportJobRunner(
            ServerRuntime serverRuntime,
            MessageSource messageSource,
            RuntimeInformationService runtimeInformationService,
            NaturalLanguageService naturalLanguageService,
            ObjectMapper objectMapper) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.messageSource = Preconditions.checkNotNull(messageSource);
        this.runtimeInformationService = Preconditions.checkNotNull(runtimeInformationService);
        this.naturalLanguageService = Preconditions.checkNotNull(naturalLanguageService);
        this.objectMapper = Preconditions.checkNotNull(objectMapper);
    }

    @Override
    public void run(JobService jobService, ReferenceDumpExportJobSpecification specification)
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
            writeData(jsonGenerator, specification);
            jsonGenerator.writeEndObject();
        }
    }

    private void writeInfo(JsonGenerator jsonGenerator) throws IOException {
        jsonGenerator.writeFieldName("info");
        objectMapper.writeValue(jsonGenerator, createArchiveInfo(serverRuntime.newContext()));
    }

    private void writeData(
            JsonGenerator jsonGenerator,
            ReferenceDumpExportJobSpecification specification) throws IOException {
        DumpExportReference dumpExportReference = createDumpExportReference(specification);
        jsonGenerator.writeFieldName("countries");
        objectMapper.writeValue(jsonGenerator, dumpExportReference.getCountries());
        jsonGenerator.writeFieldName("naturalLanguages");
        objectMapper.writeValue(jsonGenerator, dumpExportReference.getNaturalLanguages());
        jsonGenerator.writeFieldName("pkgCategories");
        objectMapper.writeValue(jsonGenerator, dumpExportReference.getPkgCategories());
        jsonGenerator.writeFieldName("userRatingStabilities");
        objectMapper.writeValue(jsonGenerator, dumpExportReference.getUserRatingStabilities());
    }

    public static Date getModifyTimestamp(ObjectContext context, RuntimeInformationService runtimeInformationService) {
        Date modifyTimestamp = GeneralQueryHelper.getLastModifyTimestampSecondAccuracy(
                context,
                Country.class, NaturalLanguage.class, PkgCategory.class);
        Date buildTimestamp = new Date(runtimeInformationService.getBuildTimestamp().toEpochMilli());
        if (buildTimestamp.getTime() > modifyTimestamp.getTime()) {
            return buildTimestamp;
        }
        return modifyTimestamp;
    }

    private ArchiveInfo createArchiveInfo(ObjectContext context) {
        return new ArchiveInfo(
                getModifyTimestamp(context, runtimeInformationService),
                runtimeInformationService.getProjectVersion());
    }

    private DumpExportReference createDumpExportReference(ReferenceDumpExportJobSpecification specification) {
        DumpExportReference dumpExportReference = new DumpExportReference();
        ObjectContext context = serverRuntime.newContext();
        NaturalLanguage naturalLanguage = NaturalLanguage.tryGetByCode(context, specification.getNaturalLanguageCode())
                .orElseGet(() -> NaturalLanguage.getEnglish(context));
        Set<NaturalLanguageCoordinates> natLangCoordsWithData = naturalLanguageService.findNaturalLanguagesWithData();
        Set<NaturalLanguageCoordinates> natLangCoordsWithLocalizations = naturalLanguageService.findNaturalLanguagesWithLocalizationMessages();

        dumpExportReference.setCountries(
                Country.getAll(context)
                        .stream()
                        .map(c -> {
                            DumpExportReferenceCountry dc = new DumpExportReferenceCountry();
                            dc.setCode(c.getCode());
                            dc.setName(c.getName());
                            return dc;
                        })
                        .collect(Collectors.toList()));

        dumpExportReference.setNaturalLanguages(
                NaturalLanguage.getAll(context)
                        .stream()
                        .filter(nl -> !specification.isFilterForSimpleTwoCharLanguageCodes()
                                || (2 == StringUtils.length(nl.getLanguageCode())
                                    && null == nl.getCountryCode()
                                    && null == nl.getScriptCode()))
                        // ^ temporary measure until R1B5 has settled and all clients can cope with more complex
                        // natural languages.
                        .map(nl -> {
                            DumpExportReferenceNaturalLanguage dnl = new DumpExportReferenceNaturalLanguage();
                            dnl.setIsPopular(nl.getIsPopular());
                            dnl.setCode(nl.getCode());
                            dnl.setLanguageCode(nl.getLanguageCode());
                            dnl.setCountryCode(nl.getCountryCode());
                            dnl.setScriptCode(nl.getScriptCode());
                            dnl.setName(
                                    messageSource.getMessage(
                                            nl.getTitleKey(),
                                            new Object[] {},
                                            naturalLanguage.toCoordinates().toLocale()));
                            dnl.setHasData(natLangCoordsWithData.contains(nl.toCoordinates()));
                            dnl.setHasLocalizationMessages(natLangCoordsWithLocalizations.contains(nl.toCoordinates()));
                            return dnl;
                        })
                        .collect(Collectors.toList()));

        dumpExportReference.setPkgCategories(
                PkgCategory.getAll(context)
                        .stream()
                        .map(pc -> {
                            DumpExportReferencePkgCategory dpc = new DumpExportReferencePkgCategory();
                            dpc.setCode(pc.getCode());
                            dpc.setName(
                                    messageSource.getMessage(
                                            pc.getTitleKey(),
                                            new Object[] {},
                                            naturalLanguage.toCoordinates().toLocale()));
                            return dpc;
                        })
                        .collect(Collectors.toList()));

        dumpExportReference.setUserRatingStabilities(
                UserRatingStability.getAll(context)
                        .stream()
                        .map(urs -> {
                            DumpExportReferenceUserRatingStability derurs = new DumpExportReferenceUserRatingStability();
                            derurs.setOrdering(urs.getOrdering().longValue());
                            derurs.setCode(urs.getCode());
                            derurs.setName(
                                    messageSource.getMessage(
                                            urs.getTitleKey(),
                                            new Object[] {},
                                            naturalLanguage.toCoordinates().toLocale()));
                            return derurs;
                        })
                        .collect(Collectors.toList()));

        return dumpExportReference;
    }

}
