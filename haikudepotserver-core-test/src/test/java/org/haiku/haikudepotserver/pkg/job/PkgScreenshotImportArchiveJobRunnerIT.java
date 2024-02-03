/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Charsets;
import com.google.common.hash.Hashing;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgScreenshot;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgSupplementModification;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotImportArchiveJobSpecification;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.ContextConfiguration;

import jakarta.annotation.Resource;
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

@ContextConfiguration(classes = TestConfig.class)
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

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "hazel", "0ee3bac6-6477-4b59-b854-0e8e1e6a6e28");
        }

        // now load in the data to the job's storage system.

        PkgScreenshotImportArchiveJobSpecification spec = new PkgScreenshotImportArchiveJobSpecification();
        spec.setOwnerUserNickname("hazel");
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "sample-pkgscreenshotimportarchive-supplied.tgz",
                MediaType.TAR.toString(),
                JobDataEncoding.GZIP,
                getResourceByteSource("sample-pkgscreenshotimportarchive-supplied.tgz")
        ).getGuid());
        spec.setImportStrategy(PkgScreenshotImportArchiveJobSpecification.ImportStrategy.REPLACE);

        // run the job to import the data

        // ------------------------------------
        String jobGuid = jobService.immediate(spec, false);
        // ------------------------------------

        JobSnapshot snapshot = jobService.tryGetJob(jobGuid).get();
        Assertions.assertThat(snapshot.getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

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

            Assertions.assertThat(outputLines.size()).isEqualTo(8);
            Assertions.assertThat(outputLines.get(1)).startsWith(
                    "\"\",\"pkg1\",\"REMOVED\",\"\",\"");
            Assertions.assertThat(outputLines.get(2)).startsWith(
                    "\"\",\"pkg1\",\"REMOVED\",\"\",\"");
            Assertions.assertThat(outputLines.get(3)).startsWith(
                    "\"hscr/pkg1/200.png\",\"pkg1\",\"ADDED\",\"\",\"");
            Assertions.assertThat(outputLines.get(4)).startsWith(
                    "\"hscr/pkg1/201.png\",\"pkg1\",\"ADDED\",\"\",\"");
            Assertions.assertThat(outputLines.get(5)).isEqualTo(
                    "\"hscr/pkg1/202.png\",\"pkg1\",\"INVALID\",,\"\"");
            Assertions.assertThat(outputLines.get(6)).startsWith(
                    "\"hscr/pkg1/23.png\",\"pkg1\",\"PRESENT\",\"\",\"");
            Assertions.assertThat(outputLines.get(7)).isEqualTo(
                    "\"hscr/notexists/3.png\",\"notexists\",\"NOTFOUND\",\"\",\"\"");

        }

    }

    /**
     * <p>The package 'pkg1' has screenshots loaded already.</p>
     */

    @Test
    public void testImport_augment() throws Exception {

        integrationTestSupportService.createStandardTestData();

        {
            ObjectContext context = serverRuntime.newContext();
            integrationTestSupportService.createBasicUser(context, "samuel", "0ee3bac6-6477-4b59-b854-0e8e1e6a6e28");
        }

        // now load in the data to the job's storage system.

        PkgScreenshotImportArchiveJobSpecification spec = new PkgScreenshotImportArchiveJobSpecification();
        spec.setOwnerUserNickname("samuel");
        spec.setInputDataGuid(jobService.storeSuppliedData(
                "sample-pkgscreenshotimportarchive-supplied.tgz",
                MediaType.TAR.toString(),
                JobDataEncoding.GZIP,
                getResourceByteSource("sample-pkgscreenshotimportarchive-supplied.tgz")
        ).getGuid());
        spec.setImportStrategy(PkgScreenshotImportArchiveJobSpecification.ImportStrategy.AUGMENT);

        // run the job to import the data

        // ------------------------------------
        String jobGuid = jobService.immediate(spec, false);
        // ------------------------------------

        JobSnapshot snapshot = jobService.tryGetJob(jobGuid).get();
        Assertions.assertThat(snapshot.getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

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

            Assertions.assertThat(outputLines).hasSize(6);
            Assertions.assertThat(outputLines.get(1)).startsWith(
                    "\"hscr/pkg1/200.png\",\"pkg1\",\"ADDED\",\"\",\"");
            Assertions.assertThat(outputLines.get(2)).startsWith(
                    "\"hscr/pkg1/201.png\",\"pkg1\",\"ADDED\",\"\",\"");
            Assertions.assertThat(outputLines.get(3)).isEqualTo(
                    "\"hscr/pkg1/202.png\",\"pkg1\",\"INVALID\",,\"\"");
            Assertions.assertThat(outputLines.get(4)).startsWith(
                    "\"hscr/pkg1/23.png\",\"pkg1\",\"PRESENT\",\"\",\"");
            Assertions.assertThat(outputLines.get(5)).isEqualTo(
                    "\"hscr/notexists/3.png\",\"notexists\",\"NOTFOUND\",\"\",\"\"");
        }

        {
            ObjectContext context = serverRuntime.newContext();
            Pkg pkg = Pkg.getByName(context, "pkg1");
            List<PkgSupplementModification> modifications = PkgSupplementModification.findForPkg(context, pkg);
            Assertions.assertThat(modifications.size()).isGreaterThanOrEqualTo(1);

            PkgSupplementModification modification = modifications.getLast();
            Assertions.assertThat(modification.getUser().getNickname()).isEqualTo("samuel");
            Assertions.assertThat(modification.getOriginSystemDescription()).isEqualTo("hds");
            Assertions.assertThat(modification.getContent()).startsWith("added screenshot [");
        }

    }

    private void assertScreenshotHashes(String[] expectedSha1Sums) {
        ObjectContext context = serverRuntime.newContext();
        Pkg pkg1 = Pkg.getByName(context, "pkg1");
        PkgSupplement pkg1Supplement = pkg1.getPkgSupplement();

        Assertions.assertThat(pkg1Supplement.getPkgScreenshots()).hasSize(expectedSha1Sums.length);
        List<PkgScreenshot> screenshots = pkg1Supplement.getSortedPkgScreenshots();

        for (int i = 0 ; i < screenshots.size(); i++) {
            PkgScreenshot screenshot = screenshots.get(i);
            String actualSha1Sum = Hashing.sha1().hashBytes(screenshot.getPkgScreenshotImage().getData()).toString();
            String expectedSha1Sum = expectedSha1Sums[i];
            Assertions.assertThat(actualSha1Sum).isEqualTo(expectedSha1Sum);
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
