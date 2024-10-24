/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.net.MediaType;
import com.opencsv.CSVWriter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.PkgServiceImpl;
import org.haiku.haikudepotserver.pkg.model.PkgNativeDesktopExportSpreadsheetJobSpecification;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;

@Component
public class PkgNativeDesktopExportSpreadsheetJobRunner
        extends AbstractJobRunner<PkgNativeDesktopExportSpreadsheetJobSpecification> {

    private static final String COLUMN_PKG_NAME = "pkg_name";
    private static final String COLUMN_IS_NATIVE_DESKTOP = "is_native_desktop";
    private static final String COLUMN_HAS_ICON = "has_icon";

    private final ServerRuntime serverRuntime;
    private final PkgServiceImpl pkgServiceImpl;

    public PkgNativeDesktopExportSpreadsheetJobRunner(
            ServerRuntime serverRuntime, PkgServiceImpl pkgServiceImpl) {
        super();
        this.serverRuntime = serverRuntime;
        this.pkgServiceImpl = pkgServiceImpl;
    }

    @Override
    public void run(
            JobService jobService,
            PkgNativeDesktopExportSpreadsheetJobSpecification specification) throws JobRunnerException {

        ObjectContext context = serverRuntime.newContext();

        try {
            // this will register the outbound data against the job.
            JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                    specification.getGuid(),
                    "download",
                    MediaType.CSV_UTF_8.toString(),
                    JobDataEncoding.NONE);

            try(
                    OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                    OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    CSVWriter writer = new CSVWriter(outputStreamWriter)
            ) {

                writer.writeNext(new String[] { COLUMN_PKG_NAME, COLUMN_IS_NATIVE_DESKTOP, COLUMN_HAS_ICON });

                pkgServiceImpl.eachPkg(context, false, (pkg) -> {
                    writer.writeNext(new String[] {
                            pkg.getName(),
                            pkg.getIsNativeDesktop() ? AbstractJobRunner.MARKER : "",
                            !pkg.getPkgSupplement().getPkgIcons().isEmpty() ? AbstractJobRunner.MARKER : "",
                    });

                    return true;
                });

            }
        }
        catch (IOException ioe) {
            throw new JobRunnerException("unable to write spreadsheet", ioe);
        }

    }

}
