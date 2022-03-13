/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

@ContextConfiguration(classes = TestConfig.class)
public class PkgVersionLocalizationCoverageExportSpreadsheetJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification spec = new PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification();

        // ------------------------------------
        String guid = jobService.submit(
                spec,
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assertions.assertThat(snapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids()
                .stream()
                .collect(SingleCollector.single());
        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();
        ByteSource expectedByteSource = getResourceByteSource("sample-pkgversionlocalizationcoverageexportspreadsheet-generated.csv");

        try(
                BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
        ) {
            assertEqualsLineByLine(sampleReader, jobReader);
        }

    }

}
