/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.csv.QuoteMode;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.dataobjects.PkgVersionLocalization;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgCategoryCoverageExportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * <p>This report is a spreadsheet that covers basic details of each package.</p>
 */

@Component
public class PkgCategoryCoverageExportSpreadsheetJobRunner extends AbstractPkgCategorySpreadsheetJobRunner<PkgCategoryCoverageExportSpreadsheetJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgCategoryCoverageExportSpreadsheetJobRunner.class);

    private final RepositoryService repositoryService;

    public PkgCategoryCoverageExportSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            PkgService pkgService,
            RepositoryService repositoryService) {
        super(serverRuntime, pkgService);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
    }


    @Override
    public Class<PkgCategoryCoverageExportSpreadsheetJobSpecification> getSupportedSpecificationClass() {
        return PkgCategoryCoverageExportSpreadsheetJobSpecification.class;
    }

    @Override
    public void run(
            JobService jobService,
            PkgCategoryCoverageExportSpreadsheetJobSpecification specification)
            throws IOException {

        Preconditions.checkArgument(null!= jobService);
        Preconditions.checkArgument(null!=specification);

        final ObjectContext context = serverRuntime.newContext();
        final List<String> pkgCategoryCodes = getPkgCategoryCodes();

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(getHeadingRow(pkgCategoryCodes))
                .setQuoteMode(QuoteMode.ALL)
                .get();

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
            long startMs = System.currentTimeMillis();

            // stream out the packages.

            LOGGER.info("will produce category coverage spreadsheet report");

            long count = pkgService.eachPkg(
                    context,
                    false,
                    pkg -> {
                        PkgSupplement pkgSupplement = pkg.getPkgSupplement();

                        List<String> cols = new ArrayList<>();
                        Optional<PkgVersionLocalization> locOptional
                                = PkgVersionLocalization.getAnyPkgVersionLocalizationForPkg(context, pkg);

                        cols.add(pkg.getName());
                        cols.add(repositoryService.getRepositoriesForPkg(context, pkg)
                                .stream()
                                .map(Repository::getCode)
                                .collect(Collectors.joining(";")));
                        cols.add(locOptional.map(pkgVersionLocalization -> pkgVersionLocalization.getSummary().orElse("")).orElse(""));
                        cols.add(pkgSupplement.getPkgPkgCategories().isEmpty() ? AbstractJobRunner.MARKER : "");

                        for (String pkgCategoryCode : pkgCategoryCodes) {
                            cols.add(pkgSupplement.getPkgPkgCategory(pkgCategoryCode).isPresent() ? AbstractJobRunner.MARKER : "");
                        }

                        cols.add(""); // no action

                        try {
                            printer.printRecord(cols.stream());
                        } catch (IOException ioe) {
                            throw new UncheckedIOException("unable to write csv line", ioe);
                        }

                        return true; // keep going!
                    }
            );

            printer.flush();
            outputStreamWriter.flush();

            LOGGER.info(
                    "did produce category coverage spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);

        }

    }

}
