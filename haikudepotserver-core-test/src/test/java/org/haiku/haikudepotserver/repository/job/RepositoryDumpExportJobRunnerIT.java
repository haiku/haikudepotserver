/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@ContextConfiguration(classes = TestConfig.class)
public class RepositoryDumpExportJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * <p>Uses the sample data and checks that the output from the report matches a captured, sensible-looking
     * previous run.</p>
     */

    @Test
    public void testRun() throws IOException {

        long now = DateTimeHelper.secondAccuracyDate(new Date()).getTime();
        integrationTestSupportService.createStandardTestData(); // creates one repo

        // ------------------------------------
        String guid = jobService.submit(
                new RepositoryDumpExportJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assertions.assertThat(snapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

        // pull in the ZIP file now and extract the data

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids().iterator().next();
        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();

        try (
                final InputStream inputStream = jobSource.getByteSource().openBufferedStream();
                final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)
        ) {

            JsonNode rootNode = objectMapper.readTree(gzipInputStream);

            JsonNode dataModifiedTimestampNode = rootNode.at("/info/dataModifiedTimestamp");
            Assertions.assertThat(dataModifiedTimestampNode.asLong()).isGreaterThanOrEqualTo(now);

            JsonNode repositoryCode = rootNode.at("/items/0/code");
            Assertions.assertThat(repositoryCode.asText()).isEqualTo("testrepo");

            JsonNode repositorySourceCode = rootNode.at("/items/0/repositorySources/0/code");
            Assertions.assertThat(repositorySourceCode.asText()).isEqualTo("testreposrc_xyz");

            JsonNode repositorySourceIdentifier = rootNode.at("/items/0/repositorySources/0/identifier");
            Assertions.assertThat(repositorySourceIdentifier.asText()).isEqualTo("http://www.example.com/test/identifier/url");

            JsonNode architectureCode = rootNode.at("/items/0/repositorySources/0/architectureCode");
            Assertions.assertThat(architectureCode.asText()).isEqualTo("x86_64");

            JsonNode mirror0CountryCode = rootNode.at("/items/0/repositorySources/0/repositorySourceMirrors/0/countryCode");
            Assertions.assertThat(mirror0CountryCode.asText()).isEqualTo("ZA");
            JsonNode mirror0BaseUrl = rootNode.at("/items/0/repositorySources/0/repositorySourceMirrors/0/baseUrl");
            Assertions.assertThat(mirror0BaseUrl.asText()).startsWith("file://");
            JsonNode mirror1BaseUrl = rootNode.at("/items/0/repositorySources/0/repositorySourceMirrors/1/baseUrl");
            Assertions.assertThat(mirror1BaseUrl.asText()).isEqualTo("file:///tmp/repository");
        }

    }

}
