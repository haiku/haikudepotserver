/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;
import com.opencsv.exceptions.CsvValidationException;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgCategoryCoverageImportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
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
    public void run(
            JobService jobService,
            PkgCategoryCoverageImportSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null != specification);
        Preconditions.checkArgument(null != specification.getInputDataGuid(), "missing imput data guid on specification");

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString());

        // if there is input data then feed it in and process it to manipulate the packages'
        // categories.

        Optional<JobDataWithByteSource> jobDataWithByteSourceOptional = jobService.tryObtainData(specification.getInputDataGuid());

        if(jobDataWithByteSourceOptional.isEmpty()) {
            throw new IllegalStateException("the job data was not able to be found for guid; " + specification.getInputDataGuid());
        }

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter);
                InputStream inputStream = jobDataWithByteSourceOptional.get().getByteSource().openStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                CSVReader reader = new CSVReader(inputStreamReader);
        ) {

            // headers

            List<String> pkgCategoryCodes = getPkgCategoryCodes();
            String[] headings = getHeadingRow(pkgCategoryCodes);

            // read in the first row of the input and check the headings are there to quasi-validate
            // that the input is not some random rubbish.

            String[] headerRow = reader.readNext();

            if(headings.length != headerRow.length) {
                throw new JobRunnerException("wrong number of header columns in input");
            }

            if(!Arrays.equals(headerRow,headings)) {
                throw new JobRunnerException("mismatched input headers");
            }

            writer.writeNext(headings);

            serverRuntime.performInTransaction(() -> {

                try {
                    String[] row;

                    while (null != (row = reader.readNext())) {
                        if (0 != row.length) {

                            ObjectContext rowContext = serverRuntime.newContext();

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

                            rowOutput.remove(rowOutput.size() - 1);
                            rowOutput.add(action.name());

                            writer.writeNext(rowOutput.toArray(new String[0]));
                        }

                    }

                } catch (Throwable th) {
                    LOGGER.error("a problem has arisen importing package categories from a spreadsheet", th);
                }

                return null;
            });

        } catch (CsvValidationException e) {
            throw new JobRunnerException("unable to validate the csv data", e);
        }

    }

}
