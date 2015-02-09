/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Optional;
import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haikuos.haikudepotserver.dataobjects.*;
import org.haikuos.haikudepotserver.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSink;
import org.haikuos.haikudepotserver.job.model.JobRunnerException;
import org.haikuos.haikudepotserver.naturallanguage.NaturalLanguageOrchestrationService;
import org.haikuos.haikudepotserver.pkg.model.PkgSearchSpecification;
import org.haikuos.haikudepotserver.pkg.model.PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification;
import org.haikuos.haikudepotserver.support.Callback;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

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
    private NaturalLanguageOrchestrationService naturalLanguageOrchestrationService;

    /**
     * <P>Returns a list of all of the natural languages sorted on the code rather than
     * the name.  It will also only return those natural languages for which there are
     * some localizations.</P>
     */

    public List<NaturalLanguage> getNaturalLanguages(ObjectContext context) {
        SelectQuery query = new SelectQuery(NaturalLanguage.class);
        query.addOrdering(new Ordering(NaturalLanguage.CODE_PROPERTY, SortOrder.ASCENDING));
        return
                ImmutableList.copyOf(
                Iterables.filter(
                        (List<NaturalLanguage>) context.performQuery(query),
                        new Predicate<NaturalLanguage>() {
                            @Override
                            public boolean apply(NaturalLanguage input) {
                                return naturalLanguageOrchestrationService.hasData(input.getCode());
                            }
                        }
                ));
    }

    @Override
    public void run(
            JobOrchestrationService jobOrchestrationService,
            PkgVersionLocalizationCoverageExportSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobOrchestrationService);
        assert null!=jobOrchestrationService;
        Preconditions.checkArgument(null!=specification);
        assert null!=specification;

        final ObjectContext context = serverRuntime.getContext();
        final List<NaturalLanguage> naturalLanguages = getNaturalLanguages(context);
        final List<Architecture> architectures = Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE));

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

            final String[] cells = new String[3 + naturalLanguages.size()];

            // headers

            cells[0] = "pkg-name";
            cells[1] = "architecture";
            cells[2] = "latest-version-coordinates";

            for(int i=0;i<naturalLanguages.size();i++) {
                cells[3 + i] = naturalLanguages.get(i).getCode();
            }

            long startMs = System.currentTimeMillis();

            writer.writeNext(cells);

            // stream out the packages.

            PkgSearchSpecification searchSpecification = new PkgSearchSpecification();
            searchSpecification.setArchitectures(Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE)));

            LOGGER.info("will produce package version localization report");

            long count = pkgOrchestrationService.eachPkg(
                    context,
                    searchSpecification,
                    new Callback<Pkg>() {
                        @Override
                        public boolean process(Pkg pkg) {

                            for(Architecture architecture : architectures) {

                                Optional<PkgVersion> pkgVersionOptional = pkgOrchestrationService.getLatestPkgVersionForPkg(
                                        context,
                                        pkg,
                                        Collections.singletonList(architecture));

                                if (pkgVersionOptional.isPresent()) {

                                    cells[0] = pkg.getName();
                                    cells[1] = architecture.getCode();
                                    cells[2] = pkgVersionOptional.get().toVersionCoordinates().toString();

                                    for(int i=0;i<naturalLanguages.size();i++) {
                                        Optional<PkgVersionLocalization> pkgVersionLocalizationOptional = pkgVersionOptional.get().getPkgVersionLocalization(naturalLanguages.get(i));
                                        cells[3 + i] = pkgVersionLocalizationOptional.isPresent() ? MARKER : "";
                                    }

                                    writer.writeNext(cells);

                                }
                            }

                            return true; // keep going!
                        }
                    }
            );

            LOGGER.info(
                    "did produce pkg version localization coverage spreadsheet report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);

        }

    }

}
