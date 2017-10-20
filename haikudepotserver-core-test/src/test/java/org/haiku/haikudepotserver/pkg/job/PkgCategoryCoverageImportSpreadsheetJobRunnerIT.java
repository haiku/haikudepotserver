/*
 * Copyright 2016-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgCategoryCoverageImportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.haiku.haikudepotserver.job.model.JobService;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.Set;
import java.util.stream.Collectors;

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
public class PkgCategoryCoverageImportSpreadsheetJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData();

        PkgCategoryCoverageImportSpreadsheetJobSpecification spec = new PkgCategoryCoverageImportSpreadsheetJobSpecification();
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "input",
                MediaType.CSV_UTF_8.toString(),
                getResourceByteSource("sample-pkgcategorycoverageimportspreadsheet-supplied.csv")
        ).getGuid());

        // ------------------------------------
        String guid = jobService.submit(
                spec,
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
        ByteSource expectedByteSource = getResourceByteSource("sample-pkgcategorycoverageimportspreadsheet-generated.csv");

        try(
                BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
        ) {
            assertEqualsLineByLine(sampleReader, jobReader);
        }

        // one of the packages was changed; check that the change is in the database successfully.

        {
            ObjectContext context = serverRuntime.newContext();
            Optional<Pkg> pkgOptional = Pkg.tryGetByName(context, "pkg1");
            Set<String> pkg1PkgCategoryCodes = pkgOptional.get().getPkgPkgCategories()
                    .stream()
                    .map(c -> c.getPkgCategory().getCode())
                    .collect(Collectors.toSet());

            Assertions.assertThat(pkg1PkgCategoryCodes).isEqualTo(new <String>HashSet(Arrays.asList("audio", "graphics")));

        }

    }

}
