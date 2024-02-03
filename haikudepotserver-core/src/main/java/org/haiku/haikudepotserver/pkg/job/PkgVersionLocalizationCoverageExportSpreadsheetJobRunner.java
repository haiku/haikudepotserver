/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import com.opencsv.CSVWriter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.pkg.model.PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * <P>This report produces a spreadsheet that outlines for each package, for which natural languages there are
 * translations present.</P>
 */

@Component
public class PkgVersionLocalizationCoverageExportSpreadsheetJobRunner
extends AbstractJobRunner<PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification> {

    private final static Logger LOGGER = LoggerFactory.getLogger(PkgVersionLocalizationCoverageExportSpreadsheetJobRunner.class);

    private final ServerRuntime serverRuntime;
    private final PkgService pkgService;
    private final RepositoryService repositoryService;
    private final NaturalLanguageService naturalLanguageService;

    public PkgVersionLocalizationCoverageExportSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            PkgService pkgService,
            RepositoryService repositoryService,
            NaturalLanguageService naturalLanguageService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgService = Preconditions.checkNotNull(pkgService);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
        this.naturalLanguageService = Preconditions.checkNotNull(naturalLanguageService);
    }

    /**
     * <P>Returns a list of all of the natural languages sorted on the code rather than
     * the name.  It will also only return those natural languages for which there are
     * some localizations.</P>
     */

    private List<NaturalLanguage> getNaturalLanguages(ObjectContext context) {
        return ObjectSelect
                .query(NaturalLanguage.class)
                .orderBy(NaturalLanguage.CODE.asc())
                .select(context)
                .stream()
                .filter(nl -> naturalLanguageService.hasData(nl.getCode()))
                .collect(Collectors.toList());
    }

    @Override
    public void run(
            final JobService jobService,
            final PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);

        final ObjectContext context = serverRuntime.newContext();

        final List<NaturalLanguage> naturalLanguages = getNaturalLanguages(context);
        final List<Architecture> architectures = Architecture.getAllExceptByCode(
                context,
                List.of(Architecture.CODE_SOURCE, Architecture.CODE_ANY));

        if(naturalLanguages.isEmpty()) {
            throw new RuntimeException("there appear to be no natural languages in the system");
        }

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

            final String[] cells = new String[4 + naturalLanguages.size()];

            // headers

            {
                int c = 0;

                cells[c++] = "pkg-name";
                cells[c++] = "repository";
                cells[c++] = "architecture";
                cells[c++] = "latest-version-coordinates";

                for (NaturalLanguage naturalLanguage : naturalLanguages) {
                    cells[c++] = naturalLanguage.getCode();
                }
            }

            long startMs = System.currentTimeMillis();

            writer.writeNext(cells);

            // stream out the packages.

            final long expectedTotal = pkgService.totalPkg(context, false);
            final AtomicLong counter = new AtomicLong(0);

            LOGGER.info("will produce package version localization report for {} packages", expectedTotal);

            long count = pkgService.eachPkg(
                    context,
                    false, // allow source only.
                    pkg -> {

                        for(Repository repository : repositoryService.getRepositoriesForPkg(context, pkg)) {

                            architectures.stream()
                                    .map(repository::tryGetRepositorySourceForArchitecture)
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .map(rs -> pkgService.getLatestPkgVersionForPkg(context, pkg, rs))
                                    .filter(Optional::isPresent)
                                    .map(Optional::get)
                                    .forEach(pv -> {
                                        int c = 0;

                                        cells[c++] = pkg.getName();
                                        cells[c++] = pv.getRepositorySource().getRepository().getCode();
                                        cells[c++] = pv.getArchitecture().getCode();
                                        cells[c++] = pv.toVersionCoordinates().toString();

                                        for (NaturalLanguage naturalLanguage : naturalLanguages) {
                                            Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = pv.getPkgVersionLocalization(naturalLanguage);
                                            cells[c++] = pkgVersionLocalizationOptional.isPresent() ? MARKER : "";
                                        }

                                        writer.writeNext(cells);
                                    });
                        }

                        jobService.setJobProgressPercent(
                                specification.getGuid(),
                                (int) ((100 * counter.incrementAndGet()) / expectedTotal));

                        return true; // keep going!
                    }
            );

            LOGGER.info(
                    "did produce pkg version localization coverage spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);

        }

    }

}
