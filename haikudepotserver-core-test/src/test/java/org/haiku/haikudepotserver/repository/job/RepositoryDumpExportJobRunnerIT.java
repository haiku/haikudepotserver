/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
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

        long now = System.currentTimeMillis();
        integrationTestSupportService.createStandardTestData(); // creates one repo

        // ------------------------------------
        String guid = jobService.submit(
                new RepositoryDumpExportJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assert.assertEquals(snapshotOptional.get().getStatus(), JobSnapshot.Status.FINISHED);

        // pull in the ZIP file now and extract the icons.

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids().iterator().next();
        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();

        try (
                final InputStream inputStream = jobSource.getByteSource().openBufferedStream();
                final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)
        ) {

            JsonNode rootNode = objectMapper.readTree(gzipInputStream);

            JsonNode dataModifiedTimestampNode = rootNode.at("/info/dataModifiedTimestamp");
            Assert.assertTrue(dataModifiedTimestampNode.asLong() >= now);

            JsonNode repositoryCode = rootNode.at("/repositories/0/code");
            Assert.assertThat(repositoryCode.asText(), CoreMatchers.is("testrepo"));

            JsonNode repositorySourceCode = rootNode.at("/repositories/0/repositorySources/0/code");
            Assert.assertThat(repositorySourceCode.asText(), CoreMatchers.is("testreposrc_xyz"));
        }

    }



}
