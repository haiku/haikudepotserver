/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Charsets;
import com.google.common.io.ByteSource;
import com.google.common.io.Resources;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgIconImportArchiveJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStream;

@ContextConfiguration(classes = TestConfig.class)
public class PkgIconImportArchiveJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    @Resource
    private PkgIconService pkgIconService;

    /**
     * <p>The package 'pkg2' has initially no icons associated with it, but one 16x16 icons is then added.  The
     * tar-ball is loaded and in doing so, two new icons are populated for 'pkg2', but the old 16x16 is not
     * present as importing for a package will remove any previously present icon data.</p>
     */

    @Test
    public void testImport() throws Exception {

        integrationTestSupportService.createStandardTestData();

        // check that there are no icons stored for pkg2.

        {
            ObjectContext context = serverRuntime.newContext();
            Assert.assertEquals(Pkg.tryGetByName(context, "pkg2").get().getPkgIcons().size(), 0);
        }

        // load in an icon for pkg2 in order to check that the removal phase does happen.

        try (InputStream iconInputStream = Resources.asByteSource(Resources.getResource("sample-16x16-2.png")).openStream()) {
            ObjectContext context = serverRuntime.newContext();
            pkgIconService.storePkgIconImage(
                    iconInputStream,
                    org.haiku.haikudepotserver.dataobjects.MediaType.tryGetByCode(context,
                            org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_PNG).get(),
                    16, // expected size along both axiis
                    context,
                    Pkg.tryGetByName(context, "pkg2").get());
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
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg2 = Pkg.tryGetByName(context, "pkg2").get();

            Assert.assertEquals(Pkg.tryGetByName(context, "pkg2").get().getPkgIcons().size(), 2);

            Assert.assertTrue(pkg2.getPkgIcon(
                    org.haiku.haikudepotserver.dataobjects.MediaType.tryGetByCode(
                            context,
                            org.haiku.haikudepotserver.dataobjects.MediaType.MEDIATYPE_HAIKUVECTORICONFILE
                    ).get(),
                    null).isPresent());

            Assert.assertTrue(pkg2.getPkgIcon(
                    org.haiku.haikudepotserver.dataobjects.MediaType.tryGetByCode(
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

            // write the report to the console in order to help with diagnosis

            LOGGER.info("actual output;\n{}",jobSource.getByteSource().asCharSource(Charsets.UTF_8).read());

            // compare actual generated with expected.

            try(
                    BufferedReader jobReader = jobSource.getByteSource().asCharSource(Charsets.UTF_8).openBufferedStream();
                    BufferedReader sampleReader = expectedByteSource.asCharSource(Charsets.UTF_8).openBufferedStream()
            ) {
                assertEqualsLineByLine(sampleReader, jobReader);
            }

        }

    }

}
