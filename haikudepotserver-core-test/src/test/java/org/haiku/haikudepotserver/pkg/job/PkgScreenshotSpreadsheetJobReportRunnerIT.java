/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;


import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class PkgScreenshotSpreadsheetJobReportRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    /**
     * <p>Uses the sample data and checks that the output from the report matches a captured, sensible-looking
     * previous run.</p>
     */

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        // ------------------------------------
        String guid = jobService.submit(
                new PkgScreenshotSpreadsheetJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assert.assertEquals(snapshotOptional.get().getStatus(), JobSnapshot.Status.FINISHED);

        String dataGuid = snapshotOptional
                .get()
                .getGeneratedDataGuids()
                .stream()
                .collect(SingleCollector.single());

        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();
        ByteSource expectedByteSource = getResourceByteSource("sample-pkgscreenshotspreadsheet-generated.csv");

        try(
                BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
        ) {
            assertEqualsLineByLine(sampleReader, jobReader);
        }

    }

}
