/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteSource;
import junit.framework.Assert;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSource;
import org.haikuos.haikudepotserver.job.model.JobSnapshot;
import org.haikuos.haikudepotserver.pkg.model.PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;

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
        Optional<String> guidOptional = jobOrchestrationService.submit(spec,JobOrchestrationService.CoalesceMode.NONE);
        // ------------------------------------

        jobOrchestrationService.awaitJobConcludedUninterruptibly(guidOptional.get(), 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobOrchestrationService.tryGetJob(guidOptional.get());
        Assert.assertEquals(snapshotOptional.get().getStatus(), JobSnapshot.Status.FINISHED);

        String dataGuid = Iterables.getOnlyElement(snapshotOptional.get().getGeneratedDataGuids());
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
