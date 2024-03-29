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
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.dataobjects.PkgSupplement;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationCoverageExportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class PkgLocalizationCoverageExportSpreadsheetJobRunner
        extends AbstractJobRunner<PkgLocalizationCoverageExportSpreadsheetJobSpecification> {

    private final static Logger LOGGER = LoggerFactory.getLogger(PkgLocalizationCoverageExportSpreadsheetJobRunner.class);

    final private ServerRuntime serverRuntime;
    final private PkgService pkgService;
    final private NaturalLanguageService naturalLanguageService;

    public PkgLocalizationCoverageExportSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            PkgService pkgService,
            NaturalLanguageService naturalLanguageService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.pkgService = Preconditions.checkNotNull(pkgService);
        this.naturalLanguageService = Preconditions.checkNotNull(naturalLanguageService);
    }

    /**
     * <P>Returns a list of all of the natural languages sorted on the code rather than
     * the name.  It will also only return those natural languages for which there are
     * some localizations.</P>
     */

    private List<NaturalLanguage> getNaturalLanguages(ObjectContext context) {
        Set<NaturalLanguageCoordinates> natLangCoordsWithData = naturalLanguageService.findNaturalLanguagesWithData();
        return ObjectSelect
                .query(NaturalLanguage.class)
                .orderBy(
                        NaturalLanguage.LANGUAGE_CODE.asc(),
                        NaturalLanguage.COUNTRY_CODE.asc(),
                        NaturalLanguage.SCRIPT_CODE.asc()
                )
                .select(context)
                .stream()
                .filter(nl -> natLangCoordsWithData.contains(nl.toCoordinates()))
                .collect(Collectors.toList());
    }

    @Override
    public void run(
            JobService jobService,
            PkgLocalizationCoverageExportSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);

        final ObjectContext context = serverRuntime.newContext();

        final List<NaturalLanguage> naturalLanguages = getNaturalLanguages(context);

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

            final String[] cells = new String[1 + naturalLanguages.size()];

            // headers

            {
                int c = 0;

                cells[c++] = "pkg-name";

                for (NaturalLanguage naturalLanguage : naturalLanguages) {
                    cells[c++] = naturalLanguage.getCode();
                }
            }

            long startMs = System.currentTimeMillis();

            writer.writeNext(cells);

            // stream out the packages.

            final long expectedTotal = pkgService.totalPkg(context, false);
            final AtomicLong counter = new AtomicLong(0);

            LOGGER.info("will produce package localization report for {} packages", expectedTotal);

            long count = pkgService.eachPkg(
                    context,
                    false, // allow source only.
                    pkg -> {

                        PkgSupplement pkgSupplement = pkg.getPkgSupplement();
                        int c = 0;
                        cells[c++] = pkg.getName();

                        for(NaturalLanguage naturalLanguage : naturalLanguages) {
                            cells[c++] = pkgSupplement.getPkgLocalization(naturalLanguage).map(pl -> MARKER).orElse("");
                        }

                        writer.writeNext(cells);

                        jobService.setJobProgressPercent(
                                specification.getGuid(),
                                (int) ((100 * counter.incrementAndGet()) / expectedTotal));

                        return true; // keep going!
                    }
            );

            LOGGER.info(
                    "did produce pkg localization coverage spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);

        }


    }
}
