/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.auto._PkgScreenshot;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.pkg.model.PkgScreenshotSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.stream.Collectors;

@Component

public class PkgScreenshotSpreadsheetJobRunner extends AbstractJobRunner<PkgScreenshotSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgScreenshotSpreadsheetJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private RepositoryService repositoryService;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Override
    public void run(
            JobService jobService,
            PkgScreenshotSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);

        final ObjectContext context = serverRuntime.getContext();

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString());

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter, ',')
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

            long count = pkgOrchestrationService.eachPkg(
                    context,
                    false,
                    pkg -> {
                        cells[0] = pkg.getName();
                        cells[1] = repositoryService.getRepositoriesForPkg(context, pkg)
                                .stream()
                                .map(Repository::getCode)
                                .collect(Collectors.joining(";"));
                        cells[2] = Integer.toString(pkg.getPkgScreenshots().size());
                        cells[3] = pkg.getPkgScreenshots()
                                .stream()
                                .collect(Collectors.summingInt(_PkgScreenshot::getLength))
                                .toString();

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
