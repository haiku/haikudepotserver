/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import com.opencsv.CSVWriter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.auto._PkgScreenshot;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.stream.Collectors;

@Component

public class PkgScreenshotSpreadsheetJobRunner extends AbstractJobRunner<PkgScreenshotSpreadsheetJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgScreenshotSpreadsheetJobRunner.class);

    private final ServerRuntime serverRuntime;
    private final RepositoryService repositoryService;
    private final PkgService pkgService;

    public PkgScreenshotSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            RepositoryService repositoryService,
            PkgService pkgService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
        this.pkgService = Preconditions.checkNotNull(pkgService);
    }

    @Override
    public Class<PkgScreenshotSpreadsheetJobSpecification> getSupportedSpecificationClass() {
        return PkgScreenshotSpreadsheetJobSpecification.class;
    }

    @Override
    public void run(
            JobService jobService,
            PkgScreenshotSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);

        final ObjectContext context = serverRuntime.newContext();

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE);

        try (
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter)
        ) {
            String[] headings = new String[]{
                    "pkg-name",
                    "repository-codes",
                    "screenshot-count",
                    "screenshot-bytes"
            };

            writer.writeNext(headings);

            String[] cells = new String[4];

            // stream out the packages.

            long startMs = System.currentTimeMillis();
            LOGGER.info("will produce spreadsheet spreadsheet report");

            long count = pkgService.eachPkg(
                    context,
                    false,
                    pkg -> {
                        PkgSupplement pkgSupplement = pkg.getPkgSupplement();

                        cells[0] = pkg.getName();
                        cells[1] = repositoryService.getRepositoriesForPkg(context, pkg)
                                .stream()
                                .map(Repository::getCode)
                                .collect(Collectors.joining(";"));
                        cells[2] = Integer.toString(pkgSupplement.getPkgScreenshots().size());
                        cells[3] = Integer.toString(pkgSupplement.getPkgScreenshots()
                                .stream()
                                .mapToInt(_PkgScreenshot::getLength)
                                .sum());

                        writer.writeNext(cells);

                        return true;
                    });

            LOGGER.info(
                    "did produce spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }

}
