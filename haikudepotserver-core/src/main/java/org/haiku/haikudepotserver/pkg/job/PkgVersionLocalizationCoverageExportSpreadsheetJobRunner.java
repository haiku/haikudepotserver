/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageService;
import org.haiku.haikudepotserver.pkg.PkgOrchestrationService;
import org.haiku.haikudepotserver.pkg.model.PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
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

    private static Logger LOGGER = LoggerFactory.getLogger(PkgVersionLocalizationCoverageExportSpreadsheetJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Resource
    private RepositoryService repositoryService;

    @Resource
    private NaturalLanguageService naturalLanguageService;

    /**
     * <P>Returns a list of all of the natural languages sorted on the code rather than
     * the name.  It will also only return those natural languages for which there are
     * some localizations.</P>
     */

    public List<NaturalLanguage> getNaturalLanguages(ObjectContext context) {
        SelectQuery query = new SelectQuery(NaturalLanguage.class);
        query.addOrdering(new Ordering(NaturalLanguage.CODE_PROPERTY, SortOrder.ASCENDING));
        return ((List<NaturalLanguage>) context.performQuery(query))
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

        final ObjectContext context = serverRuntime.getContext();

        final List<NaturalLanguage> naturalLanguages = getNaturalLanguages(context);
        final List<Architecture> architectures = Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE));

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

            final long expectedTotal = pkgOrchestrationService.totalPkg(context, false);
            final AtomicLong counter = new AtomicLong(0);

            LOGGER.info("will produce package version localization report for {} packages", expectedTotal);

            long count = pkgOrchestrationService.eachPkg(
                    context,
                    false, // allow source only.
                    pkg -> {

                        for(Repository repository : repositoryService.getRepositoriesForPkg(context, pkg)) {

                            for (Architecture architecture : architectures) {

                                Optional<PkgVersion> pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                                        context,
                                        pkg,
                                        repository,
                                        Collections.singletonList(architecture));

                                if (pkgVersionOptional.isPresent()) {
                                    int c = 0;

                                    cells[c++] = pkg.getName();
                                    cells[c++] = pkgVersionOptional.get().getRepositorySource().getRepository().getCode();
                                    cells[c++] = architecture.getCode();
                                    cells[c++] = pkgVersionOptional.get().toVersionCoordinates().toString();

                                    for (NaturalLanguage naturalLanguage : naturalLanguages) {
                                        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = pkgVersionOptional.get().getPkgVersionLocalization(naturalLanguage);
                                        cells[c++] = pkgVersionLocalizationOptional.isPresent() ? MARKER : "";
                                    }

                                    writer.writeNext(cells);

                                }
                            }

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
