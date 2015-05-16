/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSource;
import org.haikuos.haikudepotserver.job.model.JobSnapshot;
import org.haikuos.haikudepotserver.pkg.model.PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification;
import org.haikuos.haikudepotserver.support.SingleCollector;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Optional;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class PkgVersionLocalizationCoverageExportSpreadsheetJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification spec = new PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification();

        // ------------------------------------
        Optional<String> guidOptional = jobOrchestrationService.submit(spec, JobOrchestrationService.CoalesceMode.NONE);
        // ------------------------------------

        jobOrchestrationService.awaitJobConcludedUninterruptibly(guidOptional.get(), 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobOrchestrationService.tryGetJob(guidOptional.get());
        Assertions.assertThat(snapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids()
                .stream()
                .collect(SingleCollector.single());
        JobDataWithByteSource jobSource = jobOrchestrationService.tryObtainData(dataGuid).get();
        ByteSource expectedByteSource = getResourceByteSource("/sample-pkgversionlocalizationcoverageexportspreadsheet-generated.csv");

        try(
                BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
        ) {
            assertEqualsLineByLine(sampleReader, jobReader);
        }

    }

}
