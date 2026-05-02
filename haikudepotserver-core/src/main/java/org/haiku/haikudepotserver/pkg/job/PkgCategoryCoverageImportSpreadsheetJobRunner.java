/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.csv.*;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.pkg.model.PkgCategoryCoverageImportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.haiku.haikudepotserver.pkg.model.UserPkgSupplementModificationAgent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class PkgCategoryCoverageImportSpreadsheetJobRunner
        extends AbstractPkgCategorySpreadsheetJobRunner<PkgCategoryCoverageImportSpreadsheetJobSpecification> {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgCategoryCoverageImportSpreadsheetJobRunner.class);

    /**
     * <p>For the import process, this enum describes the action that was taken for a given import line.</p>
     */

    private enum Action {
        NOACTION,
        INVALID,
        UPDATED,
        NOTFOUND
    }

    public PkgCategoryCoverageImportSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            PkgService pkgService) {
        super(serverRuntime, pkgService);
    }

    @Override
    public Class<PkgCategoryCoverageImportSpreadsheetJobSpecification> getSupportedSpecificationClass() {
        return PkgCategoryCoverageImportSpreadsheetJobSpecification.class;
    }

    @Override
    public void run(
            JobService jobService,
            PkgCategoryCoverageImportSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != specification.getInputDataGuid(), "missing imput data guid on specification");
        Preconditions.checkArgument(null != specification.getOwnerUserNickname(), "the owner user must be identified");

        List<String> pkgCategoryCodes = getPkgCategoryCodes();
        String[] headings = getHeadingRow(pkgCategoryCodes);

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

        // if there is input data then feed it in and process it to manipulate the packages'
        // categories.

        Optional<JobDataWithByteSource> jobDataWithByteSourceOptional = jobService.tryObtainData(specification.getInputDataGuid());

        if(jobDataWithByteSourceOptional.isEmpty()) {
            throw new IllegalStateException("the job data was not able to be found for guid; " + specification.getInputDataGuid());
        }

        try(
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                final CSVPrinter printer = new CSVPrinter(outputStreamWriter, format);
                final InputStream inputStream = jobDataWithByteSourceOptional.get().getByteSource().openStream();
                final InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                final CSVParser parser = format.parse(inputStreamReader);
        ) {

            Iterator<CSVRecord> csvIterator = parser.iterator();

            // read in the first row of the input and check the headings are there to quasi-validate
            // that the input is not some random rubbish.

            String[] headerRow = csvIterator.next().values();

            if(headings.length != headerRow.length) {
                throw new JobRunnerException("wrong number of header columns in input");
            }

            if(!Arrays.equals(headerRow,headings)) {
                throw new JobRunnerException("mismatched input headers");
            }

            serverRuntime.performInTransaction(() -> {

                try {
                    while (csvIterator.hasNext()) {
                        String[] row = csvIterator.next().values();
                        if (0 != row.length) {

                            ObjectContext rowContext = serverRuntime.newContext();
                            User user = User.getByNickname(rowContext, specification.getOwnerUserNickname());

                            Action action = Action.NOACTION;

                            if (row.length < headings.length - 1) { // -1 because it is possible to omit the action column.
                                action = Action.INVALID;
                                LOGGER.warn("inconsistent number of cells on line");
                            } else {
                                String pkgName = row[0];
                                // 1; display
                                boolean isNone = AbstractJobRunner.MARKER.equals(row[COLUMN_NONE]);

                                Optional<Pkg> pkgOptional = Pkg.tryGetByName(rowContext, pkgName);
                                List<String> selectedPkgCategoryCodes = new ArrayList<>();

                                if (pkgOptional.isPresent()) {

                                    for (int i = 0; i < pkgCategoryCodes.size(); i++) {
                                        if (AbstractJobRunner.MARKER.equals(row[COLUMN_NONE + 1 + i].trim())) {

                                            if (isNone) {
                                                action = Action.INVALID;
                                                LOGGER.warn("line for package {} has 'none' marked as well as an actual category", row[0]);
                                            }

                                            selectedPkgCategoryCodes.add(pkgCategoryCodes.get(i));
                                        }
                                    }

                                    if (action == Action.NOACTION) {
                                        List<PkgCategory> selectedPkgCategories = PkgCategory.getByCodes(rowContext, selectedPkgCategoryCodes);

                                        if (selectedPkgCategories.size() != selectedPkgCategoryCodes.size()) {
                                            throw new IllegalStateException("one or more of the package category codes was not able to be found");
                                        }

                                        if (pkgService.updatePkgCategories(
                                                rowContext,
                                                new UserPkgSupplementModificationAgent(user),
                                                pkgOptional.get(),
                                                selectedPkgCategories)) {
                                            action = Action.UPDATED;
                                            rowContext.commitChanges();
                                            LOGGER.debug("did update for package {}", row[0]);
                                        }
                                    }

                                } else {
                                    action = Action.NOTFOUND;
                                    LOGGER.debug("unable to find the package for {}", row[0]);
                                }

                            }

                            // copy the row back verbatim, but with the action result at the
                            // end.

                            List<String> rowOutput = new ArrayList<>();
                            Collections.addAll(rowOutput, row);

                            while (rowOutput.size() < headings.length) {
                                rowOutput.add("");
                            }

                            rowOutput.removeLast();
                            rowOutput.add(action.name());

                            printer.printRecord(rowOutput);
                        }

                    }

                    printer.flush();
                    outputStreamWriter.flush();

                } catch (Throwable th) {
                    LOGGER.error("a problem has arisen importing package categories from a spreadsheet", th);
                }

                return null;
            });

        }

    }

}
