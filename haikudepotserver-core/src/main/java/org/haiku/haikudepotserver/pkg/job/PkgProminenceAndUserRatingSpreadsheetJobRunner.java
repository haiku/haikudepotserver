/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.haiku.haikudepotserver.dataobjects.PkgProminence;
import org.haiku.haikudepotserver.dataobjects.PkgUserRatingAggregate;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgProminenceAndUserRatingSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.support.SingleCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * <p>This report generates a report that lists the prominence of the packages.</p>
 */

@Component
public class PkgProminenceAndUserRatingSpreadsheetJobRunner
        extends AbstractJobRunner<PkgProminenceAndUserRatingSpreadsheetJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgProminenceAndUserRatingSpreadsheetJobRunner.class);

    private static final String[] HEADERS = new String[]{
            "pkg-name",
            "repository-code",
            "prominence-name",
            "prominence-ordering",
            "derived-rating",
            "derived-rating-sample-size"
    };

    private final ServerRuntime serverRuntime;
    private final PkgService pkgService;

    public PkgProminenceAndUserRatingSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            PkgService pkgService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgService = Preconditions.checkNotNull(pkgService);
    }

    @Override
    public Class<PkgProminenceAndUserRatingSpreadsheetJobSpecification> getSupportedSpecificationClass() {
        return PkgProminenceAndUserRatingSpreadsheetJobSpecification.class;
    }

    @Override
    public void run(JobService jobService, PkgProminenceAndUserRatingSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);

        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .get();

        final ObjectContext context = serverRuntime.newContext();

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE);

        try (
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                final CSVPrinter printer = new CSVPrinter(outputStreamWriter, format)
        ) {
            // stream out the packages.

            long startMs = System.currentTimeMillis();
            LOGGER.info("will produce prominence spreadsheet report");

            long count = pkgService.eachPkg(
                    context,
                    false,
                    pkg -> {

                        List<PkgProminence> pkgProminences = PkgProminence.findByPkg(context, pkg);
                        List<PkgUserRatingAggregate> pkgUserRatingAggregates = PkgUserRatingAggregate.findByPkg(context, pkg);
                        List<Repository> repositories = Stream.concat(
                                pkgProminences.stream().map(PkgProminence::getRepository),
                                pkgUserRatingAggregates.stream().map(PkgUserRatingAggregate::getRepository)
                        ).distinct().sorted().toList();

                        if (repositories.isEmpty()) {
                            try {
                                printer.printRecord(pkg.getName(), "", "", "", "", "");
                            } catch (IOException e) {
                                throw new UncheckedIOException("unable to write row", e);
                            }
                        } else {
                            for (Repository repository : repositories) {

                                Optional<PkgProminence> pkgProminenceOptional = pkgProminences
                                        .stream()
                                        .filter(pp -> pp.getRepository().equals(repository))
                                        .collect(SingleCollector.optional());

                                Optional<PkgUserRatingAggregate> pkgUserRatingAggregateOptional = pkgUserRatingAggregates
                                        .stream()
                                        .filter(pura -> pura.getRepository().equals(repository))
                                        .collect(SingleCollector.optional());

                                try {
                                    printer.printRecord(
                                            pkg.getName(),
                                            repository.getCode(),
                                            pkgProminenceOptional.map(p -> p.getProminence().getName()).orElse(""),
                                            pkgProminenceOptional.map(p -> p.getProminence().getOrdering().toString()).orElse(""),
                                            pkgUserRatingAggregateOptional.map(p -> p.getDerivedRating().toString()).orElse(""),
                                            pkgUserRatingAggregateOptional.map(p -> p.getDerivedRatingSampleSize().toString()).orElse("")
                                    );
                                } catch (IOException ioe) {
                                    throw new UncheckedIOException("unable to write row", ioe);
                                }
                            }
                        }

                        return true;
                    });

            LOGGER.info(
                    "did produce prominence spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }

}
