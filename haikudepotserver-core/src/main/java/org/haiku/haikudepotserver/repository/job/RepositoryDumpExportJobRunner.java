/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.job;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.auto._Repository;
import org.haiku.haikudepotserver.dataobjects.auto._RepositorySource;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.haiku.haikudepotserver.support.ArchiveInfo;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import java.util.zip.GZIPOutputStream;

/**
 * <P>Generates a JSON-dump of all of the data related to all of the repositories in the system.</P>
 */

@Component
public class RepositoryDumpExportJobRunner extends AbstractJobRunner<RepositoryDumpExportJobSpecification> {

    @Resource
    private RuntimeInformationService runtimeInformationService;

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private ObjectMapper objectMapper;

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

            ObjectContext context = serverRuntime.getContext();
            List<Repository> repositories = Repository.getAll(context)
                    .stream()
                    .filter((r) -> r.getActive())
                    .collect(Collectors.toList());

            jsonGenerator.writeStartObject();
            writeInfo(jsonGenerator, repositories);
            writeRepositories(jsonGenerator, repositories);
            jsonGenerator.writeEndObject();

        }
    }

    private void writeRepositories(JsonGenerator jsonGenerator, List<Repository> repositories) throws IOException {
        jsonGenerator.writeFieldName("repositories");
        jsonGenerator.writeStartArray();

        for (Repository repository : repositories) {
            objectMapper.writeValue(jsonGenerator, createDumpRepository(repository));
        }

        jsonGenerator.writeEndArray();
    }

    private DumpRepository createDumpRepository(Repository repository) {
        DumpRepository dumpRepository = new DumpRepository();
        dumpRepository.code = repository.getCode();
        dumpRepository.name = repository.getName();
        dumpRepository.description = repository.getDescription();
        dumpRepository.informationUrl = repository.getInformationUrl();
        dumpRepository.repositorySources = repository.getRepositorySources()
                .stream()
                .filter(_RepositorySource::getActive)
                .sorted(Comparator.comparing(_RepositorySource::getCode))
                .map((r) -> {
                    DumpRepositorySource dumpRepositorySource = new DumpRepositorySource();
                    dumpRepositorySource.code = r.getCode();
                    dumpRepositorySource.url = r.getUrl();
                    return dumpRepositorySource;
                })
                .collect(Collectors.toList());
        return dumpRepository;
    }

    private void writeInfo(JsonGenerator jsonGenerator, List<Repository> repositories) throws IOException {
        jsonGenerator.writeFieldName("info");
        objectMapper.writeValue(jsonGenerator, createArchiveInfo(repositories));
    }

    private ArchiveInfo createArchiveInfo(List<Repository> repositories) {
        Date modifyTimestamp = repositories
                .stream()
                .sorted(Comparator.comparing(Repository::getModifyTimestamp))
                .findFirst()
                .map(_Repository::getModifyTimestamp)
                .orElse(new Date(0));

        return new ArchiveInfo(
                DateTimeHelper.secondAccuracyDatePlusOneSecond(modifyTimestamp),
                runtimeInformationService.getProjectVersion());
    }

    /**
     * <p>This is a DTO representing {@link org.haiku.haikudepotserver.dataobjects.Repository}.</p>
     */

    private static final class DumpRepository {
        public String code;
        public String name;
        public String description;
        public String informationUrl;
        public List<DumpRepositorySource> repositorySources;
    }

    /**
     * <p>This is a DTO representing {@link org.haiku.haikudepotserver.dataobjects.RepositorySource}</p>
     */

    private static final class DumpRepositorySource {
        public String code;
        public String url;
    }

}
