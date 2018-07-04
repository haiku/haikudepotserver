/*
 * Copyright 2016-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security.job;

import com.google.common.net.MediaType;
import com.opencsv.CSVWriter;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.ObjectSelect;
import org.haiku.haikudepotserver.dataobjects.Permission;
import org.haiku.haikudepotserver.dataobjects.PermissionUserPkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.security.model.AuthorizationRulesSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

@Component
public class AuthorizationRulesSpreadsheetJobRunner
        extends AbstractJobRunner<AuthorizationRulesSpreadsheetJobSpecification> {

    @Resource
    private ServerRuntime serverRuntime;

    @Override
    public void run(
            JobService jobService,
            AuthorizationRulesSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        final ObjectContext context = serverRuntime.newContext();

        DateTimeFormatter dateTimeFormatter = DateTimeHelper.createStandardDateTimeFormat();

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

            writer.writeNext(new String[]{
                    "create-timestamp",
                    "user-nickname",
                    "user-active",
                    "permission-code",
                    "permission-name",
                    "pkg-name"
            });

            ObjectSelect<PermissionUserPkg> objectSelect = ObjectSelect.query(PermissionUserPkg.class)
                    .orderBy(
                            PermissionUserPkg.USER.dot(User.NICKNAME).asc(),
                            PermissionUserPkg.PERMISSION.dot(Permission.CODE).asc());

            try (ResultBatchIterator<PermissionUserPkg> batchIterator = objectSelect.batchIterator(context, 50)) {
                batchIterator.forEach((pups) -> pups.forEach((pup) -> writer.writeNext(new String[]{
                        dateTimeFormatter.format(Instant.ofEpochMilli(pup.getCreateTimestamp().getTime())),
                        pup.getUser().getNickname(),
                        Boolean.toString(pup.getUser().getActive()),
                        pup.getPermission().getCode(),
                        pup.getPermission().getName(),
                        null != pup.getPkg() ? pup.getPkg().getName() : ""
                })));
            }

            writer.flush();
            outputStreamWriter.flush();
        }

    }

}
