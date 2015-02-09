/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSink;
import org.haikuos.haikudepotserver.pkg.model.PkgIconConfiguration;
import org.haikuos.haikudepotserver.pkg.model.PkgIconSpreadsheetJobSpecification;
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
import java.util.List;

/**
 * <p>This report produces a list of icon configuration by package.</p>
 */

@Component
public class PkgIconSpreadsheetJobRunner extends AbstractJobRunner<PkgIconSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgIconSpreadsheetJobRunner.class);

    private static String MARKER = "*";

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @Override
    public void run(
            JobOrchestrationService jobOrchestrationService,
            PkgIconSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null!=jobOrchestrationService);
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
            final List<PkgIconConfiguration> pkgIconConfigurations = pkgOrchestrationService.getInUsePkgIconConfigurations(context);

            {
                List<String> headings = Lists.newArrayList();

                headings.add("pkg-name");
                headings.add("no-icons");

                for (PkgIconConfiguration pkgIconConfiguration : pkgIconConfigurations) {

                    StringBuilder heading = new StringBuilder();

                    heading.append(pkgIconConfiguration.getMediaType().getCode());

                    if (null != pkgIconConfiguration.getSize()) {
                        heading.append("@");
                        heading.append(pkgIconConfiguration.getSize().toString());
                    }

                    headings.add(heading.toString());

                }

                writer.writeNext(headings.toArray(new String[headings.size()]));
            }

            // stream out the packages.

            long startMs = System.currentTimeMillis();
            LOGGER.info("will produce icon spreadsheet report");

            PrefetchTreeNode prefetchTreeNode = new PrefetchTreeNode();
            prefetchTreeNode.addPath(Pkg.PKG_ICONS_PROPERTY);

            PkgSearchSpecification searchSpecification = new PkgSearchSpecification();
            searchSpecification.setArchitectures(Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE)));

            long count = pkgOrchestrationService.eachPkg(
                    context,
                    searchSpecification,
                    new Callback<Pkg>() {
                        @Override
                        public boolean process(Pkg pkg) {

                            List<String> cells = Lists.newArrayList();
                            cells.add(pkg.getName());

                            cells.add(pkg.getPkgIcons().isEmpty() ? MARKER : "");

                            for (PkgIconConfiguration pkgIconConfiguration : pkgIconConfigurations) {
                                cells.add(
                                        pkg.getPkgIcon(
                                                pkgIconConfiguration.getMediaType(),
                                                pkgIconConfiguration.getSize()
                                        ).isPresent()
                                                ? MARKER
                                                : "");
                            }

                            writer.writeNext(cells.toArray(new String[cells.size()]));

                            return true;
                        }
                    });

            LOGGER.info(
                    "did produce icon report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }

}
