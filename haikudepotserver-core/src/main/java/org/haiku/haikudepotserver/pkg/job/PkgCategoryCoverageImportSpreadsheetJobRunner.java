/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.job;

import au.com.bytecode.opencsv.CSVReader;
import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.access.Transaction;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.pkg.model.PkgCategoryCoverageImportSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.PkgCategory;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;

@Component
public class PkgCategoryCoverageImportSpreadsheetJobRunner
        extends AbstractPkgCategorySpreadsheetJobRunner<PkgCategoryCoverageImportSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgCategoryCoverageImportSpreadsheetJobRunner.class);

    /**
     * <p>For the import process, this enum describes the action that was taken for a given import line.</p>
     */

    private enum Action {
        NOACTION,
        INVALID,
        UPDATED,
        NOTFOUND
    }

    @Override
    public void run(
            JobService jobService,
            PkgCategoryCoverageImportSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);
        Preconditions.checkArgument(null!=specification.getInputDataGuid(), "missing imput data guid on specification");

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString());

        // if there is input data then feed it in and process it to manipulate the packages'
        // categories.

        Optional<JobDataWithByteSource> jobDataWithByteSourceOptional = jobService.tryObtainData(specification.getInputDataGuid());

        if(!jobDataWithByteSourceOptional.isPresent()) {
            throw new IllegalStateException("the job data was not able to be found for guid; " + specification.getInputDataGuid());
        }

        try(
                OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                CSVWriter writer = new CSVWriter(outputStreamWriter, ',');
                InputStream inputStream = jobDataWithByteSourceOptional.get().getByteSource().openStream();
                InputStreamReader inputStreamReader = new InputStreamReader(inputStream);
                CSVReader reader = new CSVReader(inputStreamReader);
        ) {

            // headers

            List<String> pkgCategoryCodes = getPkgCategoryCodes();
            String[] headings = getHeadingRow(pkgCategoryCodes);

            // read in the first row of the input and check the headings are there to quasi-validate
            // that the input is not some random rubbish.

            String[] row = reader.readNext();

            if(headings.length != row.length) {
                throw new JobRunnerException("wrong number of header columns in input");
            }

            if(!Arrays.equals(row,headings)) {
                throw new JobRunnerException("mismatched input headers");
            }

            writer.writeNext(headings);

            // start a cayenne long-running txn
            Transaction transaction = serverRuntime.getDataDomain().createTransaction();
            Transaction.bindThreadTransaction(transaction);

            try {

                while (null != (row = reader.readNext())) {
                    if (0 != row.length) {

                        ObjectContext rowContext = serverRuntime.getContext();

                        Action action = Action.NOACTION;

                        if (row.length < headings.length - 1) { // -1 because it is possible to omit the action column.
                            action = Action.INVALID;
                            LOGGER.warn("inconsistent number of cells on line");
                        } else {
                            String pkgName = row[0];
                            // 1; display
                            boolean isNone = AbstractJobRunner.MARKER.equals(row[2]);

                            Optional<Pkg> pkgOptional = Pkg.getByName(rowContext, pkgName);
                            List<String> selectedPkgCateogryCodes = new ArrayList<>();

                            if (pkgOptional.isPresent()) {

                                for (int i = 0; i < pkgCategoryCodes.size(); i++) {
                                    if (AbstractJobRunner.MARKER.equals(row[3 + i].trim())) {

                                        if(isNone) {
                                            action = Action.INVALID;
                                            LOGGER.warn("line for package {} has 'none' marked as well as an actual category", row[0]);
                                        }

                                        selectedPkgCateogryCodes.add(pkgCategoryCodes.get(i));
                                    }
                                }

                                if(action == Action.NOACTION) {
                                    List<PkgCategory> selectedPkgCategories = PkgCategory.getByCodes(rowContext, selectedPkgCateogryCodes);

                                    if (selectedPkgCategories.size() != selectedPkgCateogryCodes.size()) {
                                        throw new IllegalStateException("one or more of the package category codes was not able to be found");
                                    }

                                    if (pkgOrchestrationService.updatePkgCategories(
                                            rowContext,
                                            pkgOptional.get(),
                                            selectedPkgCategories)) {
                                        action = Action.UPDATED;
                                        rowContext.commitChanges();
                                        LOGGER.debug("did update for package {}", row[0]);
                                    }
                                }

                            }
                            else {
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

                        rowOutput.remove(rowOutput.size()-1);
                        rowOutput.add(action.name());

                        writer.writeNext(rowOutput.toArray(new String[rowOutput.size()]));
                    }

                }

                transaction.commit();
            }
            catch(Throwable th) {
                transaction.setRollbackOnly();
                LOGGER.error("a problem has arisen importing package categories from a spreadsheet", th);
            }
            finally {
                Transaction.bindThreadTransaction(null);

                if (Transaction.STATUS_MARKED_ROLLEDBACK == transaction.getStatus()) {
                    try {
                        transaction.rollback();
                    }
                    catch(Exception e) {
                        // ignore
                    }
                }

            }

        }

    }

}
