/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgCategoryCoverageExportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.Assert;
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
public class PkgCategoryCoverageExportSpreadsheetJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    /**
     * <p>Uses the sample data and checks that the output from the report matches a captured, sensible-looking
     * previous run.</p>
     */

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        // ------------------------------------
        Optional<String> guidOptional = jobOrchestrationService.submit(
                new PkgCategoryCoverageExportSpreadsheetJobSpecification(),
                JobOrchestrationService.CoalesceMode.NONE);
        // ------------------------------------

        jobOrchestrationService.awaitJobConcludedUninterruptibly(guidOptional.get(), 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobOrchestrationService.tryGetJob(guidOptional.get());
        Assert.assertEquals(snapshotOptional.get().getStatus(), JobSnapshot.Status.FINISHED);

        String dataGuid = snapshotOptional
                .get()
                .getGeneratedDataGuids()
                .stream()
                .collect(SingleCollector.single());

        JobDataWithByteSource jobSource = jobOrchestrationService.tryObtainData(dataGuid).get();
        ByteSource expectedByteSource = getResourceByteSource("/sample-pkgcategorycoverageexportspreadsheet-generated.csv");

        try(
                BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
        ) {
            assertEqualsLineByLine(sampleReader, jobReader);
        }

    }

}
