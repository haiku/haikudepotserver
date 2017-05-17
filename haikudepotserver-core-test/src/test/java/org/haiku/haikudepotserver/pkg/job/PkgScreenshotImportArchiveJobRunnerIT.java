/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotImportArchiveJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.hamcrest.CoreMatchers;
import org.junit.Assert;
import org.junit.Test;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.List;

/**
 * <p>The tar-ball involved in this test has the following paths;</p>
 *
 * <table>
 *     <tr>
 *         <th>path</th>
 *         <th>description</th>
 *     </tr>
 *     <tr>
 *         <td>hscr/notexists/3.png</td>
 *         <td>This package does not exist.</td>
 *     </tr>
 *     <tr>
 *         <td>hscr/pkg1/200.png</td>
 *         <td>This file is a new image that should be imported.</td>
 *     </tr>
 *     <tr>
 *         <td>hscr/pkg1/201.png</td>
 *         <td>This file is a new image that should be imported.</td>
 *     </tr>
 *     <tr>
 *         <td>hscr/pkg1/202.png</td>
 *         <td>This is an invalid PNG image.</td>
 *     </tr>
 *     <tr>
 *         <td>hscr/pkg1/23.png</td>
 *         <td>This image is already configured on the package.</td>
 *     </tr>
 * </table>
 */

@ContextConfiguration({
        "classpath:/spring/test-context.xml"
})
public class PkgScreenshotImportArchiveJobRunnerIT extends AbstractIntegrationTest {

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    /**
     * <p>The package 'pkg1' has screenshots loaded already.</p>
     */

    @Test
    public void testImport_replace() throws Exception {

        integrationTestSupportService.createStandardTestData();

        // now load in the data to the job's storage system.

        PkgScreenshotImportArchiveJobSpecification spec = new PkgScreenshotImportArchiveJobSpecification();
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "sample-pkgscreenshotimportarchive-supplied.tgz",
                MediaType.TAR.toString(),
                getResourceByteSource("sample-pkgscreenshotimportarchive-supplied.tgz")
        ).getGuid());
        spec.setImportStrategy(PkgScreenshotImportArchiveJobSpecification.ImportStrategy.REPLACE);

        // run the job to import the data

        // ------------------------------------
        String jobGuid = jobService.immediate(spec, false);
        // ------------------------------------

        JobSnapshot snapshot = jobService.tryGetJob(jobGuid).get();
        Assert.assertEquals(snapshot.getStatus(), JobSnapshot.Status.FINISHED);

        // check that the pkg1 is now loaded-up with screenshots from the tar-ball.

        assertScreenshotHashes(new String[] {
                "741dcbf503ddf034db0c41cb0fc9e3f82b20a5b6",
                "cba98ed83e44c2b07710f78c95568a3b2e3a081e",
                "fd1616327841ee2be21a16e330c57daa613580fa"
        });

        // check that the output report is as expected.

        {
            List<String> outputLines = getOutputLines(snapshot);

            // "path","pkg-name","action","message","code"
            // "","pkg1","REMOVED","","cc0bbb79-7cc1-45b2-83a0-c53b77d3d228"
            // "","pkg1","REMOVED","","52433d05-9583-4419-9033-ea6e59b0e171"
            // "hscr/pkg1/200.png","pkg1","ADDED","","b1731520-575a-4cca-8a31-afbfb561d184"
            // "hscr/pkg1/201.png","pkg1","ADDED","","7a203288-1c9e-4fa6-b055-1be62b5db2fe"
            // "hscr/pkg1/202.png","pkg1","INVALID",,""
            // "hscr/pkg1/23.png","pkg1","PRESENT","","eb5e9cf5-ca10-4f43-80c4-3ea51cde5132"
            // "hscr/notexists/3.png","notexists","NOTFOUND","",""

            // compare actual generated with expected.

            Assert.assertThat(outputLines.size(), CoreMatchers.is(8));
            Assert.assertThat(outputLines.get(1),
                    CoreMatchers.startsWith("\"\",\"pkg1\",\"REMOVED\",\"\",\""));
            Assert.assertThat(outputLines.get(2),
                    CoreMatchers.startsWith("\"\",\"pkg1\",\"REMOVED\",\"\",\""));
            Assert.assertThat(outputLines.get(3),
                    CoreMatchers.startsWith("\"hscr/pkg1/200.png\",\"pkg1\",\"ADDED\",\"\",\""));
            Assert.assertThat(outputLines.get(4),
                    CoreMatchers.startsWith("\"hscr/pkg1/201.png\",\"pkg1\",\"ADDED\",\"\",\""));
            Assert.assertThat(outputLines.get(5),
                    CoreMatchers.is("\"hscr/pkg1/202.png\",\"pkg1\",\"INVALID\",,\"\""));
            Assert.assertThat(outputLines.get(6),
                    CoreMatchers.startsWith("\"hscr/pkg1/23.png\",\"pkg1\",\"PRESENT\",\"\",\""));
            Assert.assertThat(outputLines.get(7),
                    CoreMatchers.is("\"hscr/notexists/3.png\",\"notexists\",\"NOTFOUND\",\"\",\"\""));

        }

    }

    /**
     * <p>The package 'pkg1' has screenshots loaded already.</p>
     */

    @Test
    public void testImport_augment() throws Exception {

        integrationTestSupportService.createStandardTestData();

        // now load in the data to the job's storage system.

        PkgScreenshotImportArchiveJobSpecification spec = new PkgScreenshotImportArchiveJobSpecification();
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "sample-pkgscreenshotimportarchive-supplied.tgz",
                MediaType.TAR.toString(),
                getResourceByteSource("sample-pkgscreenshotimportarchive-supplied.tgz")
        ).getGuid());
        spec.setImportStrategy(PkgScreenshotImportArchiveJobSpecification.ImportStrategy.AUGMENT);

        // run the job to import the data

        // ------------------------------------
        String jobGuid = jobService.immediate(spec, false);
        // ------------------------------------

        JobSnapshot snapshot = jobService.tryGetJob(jobGuid).get();
        Assert.assertEquals(snapshot.getStatus(), JobSnapshot.Status.FINISHED);

        // check that the pkg1 is now loaded-up with screenshots from the tar-ball.

        assertScreenshotHashes(new String[] {
                "741dcbf503ddf034db0c41cb0fc9e3f82b20a5b6",
                "199a10b2b498e07e5bde31d816b560b19ed76ca6",
                "1a7be578f99d815084d32487d0e74626fe489967",
                "cba98ed83e44c2b07710f78c95568a3b2e3a081e",
                "fd1616327841ee2be21a16e330c57daa613580fa"
        });

        // check that the output report is as expected.

        {
            List<String> outputLines = getOutputLines(snapshot);

            // "path","pkg-name","action","message","code"
            // "hscr/pkg1/200.png","pkg1","ADDED","","34e796f2-27ff-4139-a0eb-b3b0e8238e96"
            // "hscr/pkg1/201.png","pkg1","ADDED","","91da40b7-762d-4d43-a0a0-4b4f31e585ae"
            // "hscr/pkg1/202.png","pkg1","INVALID",,""
            // "hscr/pkg1/23.png","pkg1","PRESENT","","2ef86afa-4da3-4f0c-9be6-d2b54ff099cb"
            // "hscr/notexists/3.png","notexists","NOTFOUND","",""

            // compare actual generated with expected.

            Assert.assertThat(outputLines.size(), CoreMatchers.is(6));
            Assert.assertThat(outputLines.get(1),
                    CoreMatchers.startsWith("\"hscr/pkg1/200.png\",\"pkg1\",\"ADDED\",\"\",\""));
            Assert.assertThat(outputLines.get(2),
                    CoreMatchers.startsWith("\"hscr/pkg1/201.png\",\"pkg1\",\"ADDED\",\"\",\""));
            Assert.assertThat(outputLines.get(3),
                    CoreMatchers.is("\"hscr/pkg1/202.png\",\"pkg1\",\"INVALID\",,\"\""));
            Assert.assertThat(outputLines.get(4),
                    CoreMatchers.startsWith("\"hscr/pkg1/23.png\",\"pkg1\",\"PRESENT\",\"\",\""));
            Assert.assertThat(outputLines.get(5),
                    CoreMatchers.is("\"hscr/notexists/3.png\",\"notexists\",\"NOTFOUND\",\"\",\"\""));

        }

    }

    private void assertScreenshotHashes(String[] expectedSha1Sums) {
        ObjectContext context = serverRuntime.getContext();
        Pkg pkg1 = Pkg.getByName(context, "pkg1");

        Assert.assertThat(pkg1.getPkgScreenshots().size(), CoreMatchers.is(expectedSha1Sums.length));

        List<PkgScreenshot> screenshots = pkg1.getSortedPkgScreenshots();

        for (int i = 0 ; i < screenshots.size(); i++) {
            PkgScreenshot screenshot = screenshots.get(i);
            String actualSha1Sum = Hashing.sha1().hashBytes(screenshot.getPkgScreenshotImage().getData()).toString();
            String expectedSha1Sum = expectedSha1Sums[i];
            Assert.assertThat(actualSha1Sum, CoreMatchers.is(expectedSha1Sum));
        }
    }

    private List<String> getOutputLines(JobSnapshot snapshot) throws IOException {
        String dataGuid = snapshot
                .getGeneratedDataGuids()
                .stream()
                .collect(SingleCollector.single());

        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();

        // write the report to the console in order to help with diagnosis

        LOGGER.info("actual output;\n{}", jobSource.getByteSource().asCharSource(Charsets.UTF_8).read());
        return jobSource.getByteSource().asCharSource(Charsets.UTF_8).readLines();
    }

}
