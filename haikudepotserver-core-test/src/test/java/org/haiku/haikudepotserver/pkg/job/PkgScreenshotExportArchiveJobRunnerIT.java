/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.collect.Sets;
import com.google.common.io.ByteSource;
import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.fest.assertions.Assertions;
import org.haiku.haikudepotserver.AbstractIntegrationTest;
import org.haiku.haikudepotserver.IntegrationTestSupportService;
import org.haiku.haikudepotserver.WrapWithNoCloseInputStream;
import org.haiku.haikudepotserver.config.TestConfig;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotExportArchiveJobSpecification;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.context.ContextConfiguration;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;

@ContextConfiguration(classes = TestConfig.class)
public class PkgScreenshotExportArchiveJobRunnerIT extends AbstractIntegrationTest {


    private static Logger LOGGER = LoggerFactory.getLogger(PkgIconExportArchiveJobRunnerIT.class);

    @Resource
    private IntegrationTestSupportService integrationTestSupportService;

    @Resource
    private JobService jobService;

    /**
     * <p>Uses the sample data and checks that the output from the report matches a captured, sensible-looking
     * previous run.</p>
     */

    @Test
    public void testRun() throws IOException {

        integrationTestSupportService.createStandardTestData(); // pkg1 has some icons

        // ------------------------------------
        String guid = jobService.submit(
                new PkgScreenshotExportArchiveJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_NONE);
        // ------------------------------------

        jobService.awaitJobFinishedUninterruptibly(guid, 10000);
        Optional<? extends JobSnapshot> snapshotOptional = jobService.tryGetJob(guid);
        Assertions.assertThat(snapshotOptional.get().getStatus()).isEqualTo(JobSnapshot.Status.FINISHED);

        // pull in the ZIP file now and extract the icons.

        String dataGuid = snapshotOptional.get().getGeneratedDataGuids().iterator().next();
        JobDataWithByteSource jobSource = jobService.tryObtainData(dataGuid).get();

        try (
                final InputStream inputStream = jobSource.getByteSource().openBufferedStream();
                final GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                final TarArchiveInputStream tarInputStream = new TarArchiveInputStream(gzipInputStream);
        ) {

            ArchiveEntry tarEntry;
            Pattern pngPattern = Pattern.compile("^" + PkgScreenshotExportArchiveJobRunner.PATH_COMPONENT_TOP + "/pkg1/([0-9]+).png$");
            ByteSource zipNoCloseInputStreamByteSource = new ByteSource() {
                @Override
                public InputStream openStream() throws IOException {
                    return new WrapWithNoCloseInputStream(tarInputStream);
                }
            };

            ByteSource expectedScreenshotByteSource = getResourceByteSource("sample-320x240-a.png");

            Set<String> foundPkg1Filenames = Sets.newHashSet();

            while(null != (tarEntry = tarInputStream.getNextEntry())) {

                if(tarEntry.getName().contains("/pkg1/")) {
                    Matcher matcher = pngPattern.matcher(tarEntry.getName());

                    if (matcher.matches()) {
                        expectedScreenshotByteSource.contentEquals(zipNoCloseInputStreamByteSource);
                        foundPkg1Filenames.add(matcher.group(1) + ".png");
                    }
                    else {
                        org.junit.jupiter.api.Assertions.fail("the zip entry has an unknown file; " + tarEntry.getName());
                    }
                }
                else {
                    LOGGER.info("ignoring; {}", tarEntry.getName());
                }
            }

            Assertions.assertThat(foundPkg1Filenames).contains(
                    "1.png",
                    "2.png",
                    "3.png");

        }

    }

}
