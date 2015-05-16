/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haikuos.haikudepotserver.AbstractIntegrationTest;
import org.haikuos.haikudepotserver.IntegrationTestSupportService;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.PkgPkgCategory;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSource;
import org.haikuos.haikudepotserver.job.model.JobSnapshot;
import org.haikuos.haikudepotserver.pkg.model.PkgCategoryCoverageImportSpreadsheetJobSpecification;
import org.haikuos.haikudepotserver.support.SingleCollector;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@ContextConfiguration({
        "classpath:/spring/servlet-context.xml",
        "classpath:/spring/test-context.xml"
})
public class PkgCategoryCoverageImportSpreadsheetJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        PkgCategoryCoverageImportSpreadsheetJobSpecification spec = new PkgCategoryCoverageImportSpreadsheetJobSpecification();
        spec.setInputDataGuid(jobOrchestrationService.storeSuppliedData(
                "input",
                MediaType.CSV_UTF_8.toString(),
                getResourceByteSource("/sample-pkgcategorycoverageimportspreadsheet-supplied.csv")
        ).getGuid());

        // ------------------------------------
        Optional<String> guidOptional = jobOrchestrationService.submit(spec, JobOrchestrationService.CoalesceMode.NONE);
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
        ByteSource expectedByteSource = getResourceByteSource("/sample-pkgcategorycoverageimportspreadsheet-generated.csv");

        try(
                BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
        ) {
            assertEqualsLineByLine(sampleReader, jobReader);
        }

        // one of the packages was changed; check that the change is in the database successfully.

        {
            ObjectContext context = serverRuntime.getContext();
            Optional<Pkg> pkgOptional = Pkg.getByName(context, "pkg1");
            Set<String> pkg1PkgCategoryCodes = pkgOptional.get().getPkgPkgCategories()
                    .stream()
                    .map(c -> c.getPkgCategory().getCode())
                    .collect(Collectors.toSet());

            Assertions.assertThat(pkg1PkgCategoryCodes).isEqualTo(new <String>HashSet(Arrays.asList("audio", "graphics")));

        }

    }

}
