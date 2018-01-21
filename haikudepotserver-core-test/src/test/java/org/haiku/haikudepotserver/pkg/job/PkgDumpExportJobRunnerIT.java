/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgDumpExportJobSpecification;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.Optional;
import java.util.zip.GZIPInputStream;

/**
 * <p>This will be a fairly loose test; it just checks that the process
 * runs smoothly and that some basic data is present.</p>
 */

@ContextConfiguration(classes = TestConfig.class)
public class PkgDumpExportJobRunnerIT extends AbstractIntegrationTest {


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

        integrationTestSupportService.createStandardTestData();

        PkgDumpExportJobSpecification specification = new PkgDumpExportJobSpecification();
        specification.setRepositorySourceCode("testreposrc_xyz");
        specification.setNaturalLanguageCode("es");

        // ------------------------------------
        String guid = jobService.submit(
                specification,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assert.assertEquals(snapshotOptional.get().getStatus(), JobSnapshot.Status.FINISHED);

        // pull in the ZIP file now and extract the data

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids().iterator().next();
        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();

        try (
                final InputStream inputStream = jobSource.getByteSource().openBufferedStream();
                final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream)
        ) {
            JsonNode rootNode = objectMapper.readTree(gzipInputStream);

            JsonNode dataModifiedTimestampNode = rootNode.at("/info/dataModifiedTimestamp");
            Assert.assertTrue(dataModifiedTimestampNode.asLong() >= now);

            JsonNode repositoryCode = rootNode.at("/items/0/name");
            Assert.assertThat(repositoryCode.asText(), CoreMatchers.is("pkg1"));

            JsonNode derivedRating = rootNode.at("/items/0/derivedRating");
            Assert.assertThat(derivedRating.asText(), CoreMatchers.is("3.5"));

            JsonNode pkgScreenshots = rootNode.at("/items/0/pkgScreenshots/0/length");
            Assert.assertThat(pkgScreenshots.asLong(), CoreMatchers.is(41296L));

            JsonNode pkgCategories = rootNode.at("/items/0/pkgCategories/0/code");
            Assert.assertThat(pkgCategories.asText(), CoreMatchers.is("graphics"));

            JsonNode pv0Summary = rootNode.at("/items/0/pkgVersions/0/summary");
            Assert.assertThat(pv0Summary.asText(), CoreMatchers.is("pkg1Version2SummarySpanish_feijoa"));
        }

    }

}
