/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.job;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Architecture;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.auto._Repository;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySource;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySourceMirror;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.haiku.haikudepotserver.repository.model.dumpexport.DumpExportRepository;
import org.haiku.haikudepotserver.repository.model.dumpexport.DumpExportRepositorySource;
import org.haiku.haikudepotserver.repository.model.dumpexport.DumpExportRepositorySourceMirror;
import org.haiku.haikudepotserver.support.ArchiveInfo;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * <P>Generates a JSON-dump of all of the data related to all of the repositories in the system.</P>
 */

@Component
public class RepositoryDumpExportJobRunner extends AbstractJobRunner<RepositoryDumpExportJobSpecification> {

    private final ServerRuntime serverRuntime;

    private final RuntimeInformationService runtimeInformationService;

    private final ObjectMapper objectMapper;

    public RepositoryDumpExportJobRunner(
            ServerRuntime serverRuntime,
            RuntimeInformationService runtimeInformationService,
            ObjectMapper objectMapper) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.runtimeInformationService = Preconditions.checkNotNull(runtimeInformationService);
        this.objectMapper = Preconditions.checkNotNull(objectMapper);
    }

    @Override
    public void run(JobService jobService, RepositoryDumpExportJobSpecification specification)
            throws IOException, JobRunnerException {

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.JSON_UTF_8.toString());

        try(
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final GZIPOutputStream gzipOutputStream = new GZIPOutputStream(outputStream);
                final JsonGenerator jsonGenerator = objectMapper.getFactory().createGenerator(gzipOutputStream)
                ) {

            ObjectContext context = serverRuntime.newContext();
            List<Repository> repositories = Repository.getAll(context)
                    .stream()
                    .filter(_Repository::getActive)
                    .collect(Collectors.toList());

            jsonGenerator.writeStartObject();
            writeInfo(jsonGenerator, repositories);
            writeRepositories(jsonGenerator, repositories);
            jsonGenerator.writeEndObject();

        }
    }

    private void writeRepositories(JsonGenerator jsonGenerator, List<Repository> repositories) throws IOException {
        jsonGenerator.writeFieldName("items");
        jsonGenerator.writeStartArray();

        for (Repository repository : repositories) {
            objectMapper.writeValue(jsonGenerator, createDumpRepository(repository));
        }

        jsonGenerator.writeEndArray();
    }

    private DumpExportRepository createDumpRepository(Repository repository) {
        DumpExportRepository dumpRepository = new DumpExportRepository();
        dumpRepository.setCode(repository.getCode());
        dumpRepository.setName(repository.getName());
        dumpRepository.setDescription(repository.getDescription());
        dumpRepository.setInformationUrl(repository.getInformationUrl());
        dumpRepository.setRepositorySources(repository.getRepositorySources()
                .stream()
                .filter(_RepositorySource::getActive)
                .filter(rs -> rs.tryGetPrimaryMirror().isPresent())
                .sorted(Comparator.comparing(_RepositorySource::getCode))
                .map((rs) -> {
                    DumpExportRepositorySource dumpRepositorySource = new DumpExportRepositorySource();
                    dumpRepositorySource.setCode(rs.getCode());
                    dumpRepositorySource.setArchitectureCode(
                            Optional.ofNullable(rs.getArchitecture())
                                    .map(Architecture::getCode)
                                    .orElse(null));
                    dumpRepositorySource.setIdentifier(rs.getIdentifier());
                    dumpRepositorySource.setRepoInfoUrl(rs.getIdentifier());
                    // ^^ deprecated; repoInfoUrl is replaced with identifier
                    dumpRepositorySource.setExtraIdentifiers(rs.getExtraIdentifiers());

                    dumpRepositorySource.setRepositorySourceMirrors(
                            rs.getRepositorySourceMirrors()
                                    .stream()
                                    .filter(_RepositorySourceMirror::getActive)
                                    .sorted(Comparator.comparing(_RepositorySourceMirror::getCode))
                                    .map(rsm -> {
                                        DumpExportRepositorySourceMirror dumpMirror = new DumpExportRepositorySourceMirror();
                                        dumpMirror.setBaseUrl(rsm.getBaseUrl());
                                        dumpMirror.setCountryCode(rsm.getCountry().getCode());
                                dumpMirror.setDescription(rsm.getDescription());
                                dumpMirror.setIsPrimary(rsm.getIsPrimary());
                                return dumpMirror;
                            })
                            .collect(Collectors.toList()));
                    return dumpRepositorySource;
                })
                .collect(Collectors.toList())
        );
        return dumpRepository;
    }

    private void writeInfo(JsonGenerator jsonGenerator, List<Repository> repositories) throws IOException {
        jsonGenerator.writeFieldName("info");
        objectMapper.writeValue(jsonGenerator, createArchiveInfo(repositories));
    }

    private ArchiveInfo createArchiveInfo(List<Repository> repositories) {
        Date modifyTimestamp = repositories
                .stream().max(Comparator.comparing(Repository::getModifyTimestamp))
                .map(_Repository::getModifyTimestamp)
                .orElse(new java.sql.Timestamp(0L));

        return new ArchiveInfo(
                DateTimeHelper.secondAccuracyDatePlusOneSecond(modifyTimestamp),
                runtimeInformationService.getProjectVersion());
    }

}
