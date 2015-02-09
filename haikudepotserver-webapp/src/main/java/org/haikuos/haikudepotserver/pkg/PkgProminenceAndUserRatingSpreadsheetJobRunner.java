/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSink;
import org.haikuos.haikudepotserver.pkg.model.PkgProminenceAndUserRatingSpreadsheetJobSpecification;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.support.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;

/**
 * <p>This report generates a report that lists the prominence of the packages.</p>
 */

@Component
public class PkgProminenceAndUserRatingSpreadsheetJobRunner
        extends AbstractJobRunner<PkgProminenceAndUserRatingSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgProminenceAndUserRatingSpreadsheetJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Override
    public void run(JobOrchestrationService jobOrchestrationService, PkgProminenceAndUserRatingSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobOrchestrationService);
        assert null!=jobOrchestrationService;
        Preconditions.checkArgument(null!=specification);

        final ObjectContext context = serverRuntime.getContext();

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobOrchestrationService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString());

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter, ',')
        ) {

            writer.writeNext(new String[]{
                    "pkg-name",
                    "prominence-name",
                    "prominence-ordering",
                    "derived-rating",
                    "derived-rating-sample-size"
            });

            // stream out the packages.

            long startMs = System.currentTimeMillis();
            LOGGER.info("will produce prominence spreadsheet report");

            PkgSearchSpecification searchSpecification = new PkgSearchSpecification();
            searchSpecification.setArchitectures(Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE)));

            long count = pkgOrchestrationService.eachPkg(
                    context,
                    searchSpecification,
                    new Callback<Pkg>() {
                        @Override
                        public boolean process(Pkg pkg) {

                            writer.writeNext(
                                    new String[]{
                                            pkg.getName(),
                                            pkg.getProminence().getName(),
                                            pkg.getProminence().getOrdering().toString(),
                                            null == pkg.getDerivedRating() ? "" : pkg.getDerivedRating().toString(),
                                            null == pkg.getDerivedRating() ? "" : pkg.getDerivedRatingSampleSize().toString()
                                    }
                            );

                            return true;
                        }
                    });

            LOGGER.info(
                    "did produce prominence spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }

}
