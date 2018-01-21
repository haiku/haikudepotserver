/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgIconConfiguration;
import org.haiku.haikudepotserver.pkg.model.PkgIconService;
import org.haiku.haikudepotserver.pkg.model.PkgIconSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * <p>This report produces a list of icon configuration by package.</p>
 */

@Component
public class PkgIconSpreadsheetJobRunner extends AbstractJobRunner<PkgIconSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgIconSpreadsheetJobRunner.class);

    private static String MARKER = "*";

    private final ServerRuntime serverRuntime;
    private final RepositoryService repositoryService;
    private final PkgService pkgService;
    private final PkgIconService pkgIconService;

    public PkgIconSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            RepositoryService repositoryService,
            PkgService pkgService,
            PkgIconService pkgIconService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
        this.pkgService = Preconditions.checkNotNull(pkgService);
        this.pkgIconService = Preconditions.checkNotNull(pkgIconService);
    }

    @Override
    public void run(
            JobService jobService,
            PkgIconSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null!= jobService);
        Preconditions.checkArgument(null!=specification);

        final ObjectContext context = serverRuntime.newContext();

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
            final List<PkgIconConfiguration> pkgIconConfigurations = pkgIconService.getInUsePkgIconConfigurations(context);

            {
                List<String> headings = new ArrayList<>();

                headings.add("pkg-name");
                headings.add("repository-codes");
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

            long count = pkgService.eachPkg(
                    context,
                    false,
                    pkg -> {

                        List<String> cells = new ArrayList<>();
                        cells.add(pkg.getName());
                        cells.add(repositoryService.getRepositoriesForPkg(context, pkg)
                                .stream()
                                .map(Repository::getCode)
                                .collect(Collectors.joining(";")));
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
                    });

            LOGGER.info(
                    "did produce icon report for {} packages in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }

}
