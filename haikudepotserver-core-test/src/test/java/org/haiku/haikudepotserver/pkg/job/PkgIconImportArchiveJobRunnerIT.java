/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgIconImportArchiveJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.IOException;

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
public class PkgIconImportArchiveJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    /**
     * <p>This test relies on the fact that pkg1 in the standard test data has some icons associated with it, but
     * there are no icons stored for pkg2.</p>
     */

    @Test
    public void testImport() throws IOException {

        integrationTestSupportService.createStandardTestData();

        // check that there are no icons stored for pkg2.

        {
            ObjectContext context = serverRuntime.getContext();
            Assert.assertEquals(Pkg.getByName(context, "pkg2").get().getPkgIcons().size(), 0);
        }

        // now load in the data to the job's storage system.

        PkgIconImportArchiveJobSpecification spec = new PkgIconImportArchiveJobSpecification();
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "sample-pkgiconimportarchive-supplied.tgz",
                MediaType.TAR.toString(),
                getResourceByteSource("sample-pkgiconimportarchive-supplied.tgz")
        ).getGuid());

        // run the job to import the data

        // ------------------------------------
        String jobGuid = jobService.immediate(spec, false);
        // ------------------------------------

        JobSnapshot snapshot = jobService.tryGetJob(jobGuid).get();
        Assert.assertEquals(snapshot.getStatus(), JobSnapshot.Status.FINISHED);

        // check that the pkg2 is now loaded-up with icons from the tar-ball.

        {
            ObjectContext context = serverRuntime.getContext();
            Pkg pkg2 = Pkg.getByName(context, "pkg2").get();

            Assert.assertEquals(Pkg.getByName(context, "pkg2").get().getPkgIcons().size(), 2);

            Assert.assertTrue(pkg2.getPkgIcon(
                    org.haiku.haikudepotserver.dataobjects.MediaType.getByCode(
                            context,
                            org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE
                    ).get(),
                    null).isPresent());

            Assert.assertTrue(pkg2.getPkgIcon(
                    org.haiku.haikudepotserver.dataobjects.MediaType.getByCode(
                            context,
                            org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_PNG
                    ).get(),
                    32).isPresent());
        }

        // check that the output report is as expected.

        {

            String dataGuid = snapshot
                    .getGeneratedDataGuids()
                    .stream()
                    .collect(SingleCollector.single());

            JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();
            ByteSource expectedByteSource = getResourceByteSource("sample-pkgiconimportarchive-generated.csv");

            try(
                    BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                    BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
            ) {
                assertEqualsLineByLine(sampleReader, jobReader);
            }

        }

    }


}
