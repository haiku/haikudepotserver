/*
 * Copyright 2024-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
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
import java.io.UncheckedIOException;

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
    public Class<PkgNativeDesktopExportSpreadsheetJobSpecification> getSupportedSpecificationClass() {
        return PkgNativeDesktopExportSpreadsheetJobSpecification.class;
    }

    @Override
    public void run(
            JobService jobService,
            PkgNativeDesktopExportSpreadsheetJobSpecification specification) throws JobRunnerException {

        ObjectContext context = serverRuntime.newContext();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(COLUMN_PKG_NAME, COLUMN_IS_NATIVE_DESKTOP, COLUMN_HAS_ICON)
                .get();

        try {
            // this will register the outbound data against the job.
            JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                    specification.getGuid(),
                    "download",
                    MediaType.CSV_UTF_8.toString(),
                    JobDataEncoding.NONE);

            try(
                    final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                    final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                    final CSVPrinter printer = new CSVPrinter(outputStreamWriter, format)
            ) {
                pkgServiceImpl.eachPkg(context, false, (pkg) -> {
                    try {
                        printer.printRecord(
                                pkg.getName(),
                                pkg.getIsNativeDesktop() ? AbstractJobRunner.MARKER : "",
                                !pkg.getPkgSupplement().getPkgIcons().isEmpty() ? AbstractJobRunner.MARKER : ""
                        );
                    } catch (IOException ioe) {
                        throw new UncheckedIOException("unable to write row", ioe);
                    }

                    return true;
                });

            }
        }
        catch (IOException ioe) {
            throw new JobRunnerException("unable to write spreadsheet", ioe);
        }

    }

}
