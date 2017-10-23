/*
 * Copyright 2015-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.model.PkgLocalizationCoverageExportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Component
public class PkgLocalizationCoverageExportSpreadsheetJobRunner
        extends AbstractJobRunner<PkgLocalizationCoverageExportSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgLocalizationCoverageExportSpreadsheetJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgService pkgService;

    @Resource
    private NaturalLanguageService naturalLanguageService;

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
                MediaType.CSV_UTF_8.toString());

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter, ',')
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

                        int c = 0;
                        cells[c++] = pkg.getName();

                        for(NaturalLanguage naturalLanguage : naturalLanguages) {
                            cells[c++] = pkg.getPkgLocalization(naturalLanguage).isPresent() ? MARKER : "";
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
