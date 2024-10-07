/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.net.MediaType;
import com.opencsv.CSVWriter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgNativeDesktopExportSpreadsheetJobSpecification;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;

@Component
public class PkgNativeDesktopExportSpreadsheetJobRunner
        extends AbstractJobRunner<PkgNativeDesktopExportSpreadsheetJobSpecification> {

    private static final String COLUMN_PKG_NAME = "pkg_name";
    private static final String COLUMN_IS_NATIVE_DESKTOP = "is_native_desktop";

    private static final int BATCH_SIZE = 100;

    private final ServerRuntime serverRuntime;

    public PkgNativeDesktopExportSpreadsheetJobRunner(
            ServerRuntime serverRuntime) {
        super();
        this.serverRuntime = serverRuntime;
    }

    @Override
    public void run(
            JobService jobService,
            PkgNativeDesktopExportSpreadsheetJobSpecification specification) throws JobRunnerException {

        ObjectSelect<Pkg> query = ObjectSelect.query(Pkg.class).where(Pkg.ACTIVE.isTrue()).orderBy(Pkg.NAME.asc()).limit(BATCH_SIZE);
        ObjectContext context = serverRuntime.newContext();
        int count = 0;

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

                writer.writeNext(new String[] { COLUMN_PKG_NAME, COLUMN_IS_NATIVE_DESKTOP });

                while (true) {
                    List<Pkg> pkgs = query.offset(count).select(context);

                    if (pkgs.isEmpty()) {
                        return;
                    }

                    for (Pkg pkg : pkgs) {
                        writer.writeNext(new String[] {
                                pkg.getName(),
                                pkg.getIsNativeDesktop() ? AbstractJobRunner.MARKER : ""
                        });
                    }

                    count += pkgs.size();
                }
            }
        }
        catch (IOException ioe) {
            throw new JobRunnerException("unable to write spreadsheet", ioe);
        }

    }

}
