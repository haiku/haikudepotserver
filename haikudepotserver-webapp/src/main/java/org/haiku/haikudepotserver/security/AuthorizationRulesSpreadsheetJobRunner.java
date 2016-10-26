/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.security;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.Ordering;
import org.apache.cayenne.query.SelectQuery;
import org.apache.cayenne.query.SortOrder;
import org.haiku.haikudepotserver.dataobjects.Permission;
import org.haiku.haikudepotserver.dataobjects.PermissionUserPkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.security.model.AuthorizationRulesSpreadsheetJobSpecification;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
public class AuthorizationRulesSpreadsheetJobRunner
        extends AbstractJobRunner<AuthorizationRulesSpreadsheetJobSpecification> {

    private static final int LIMIT_GENERATECSV = 100;

    @Resource
    private ServerRuntime serverRuntime;

    @Override
    public void run(
            JobOrchestrationService jobOrchestrationService,
            AuthorizationRulesSpreadsheetJobSpecification specification)
            throws IOException, JobRunnerException {

        final ObjectContext context = serverRuntime.getContext();

        DateTimeFormatter dateTimeFormatter = DateTimeHelper.createStandardDateTimeFormat();

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

            writer.writeNext(new String[]{
                    "create-timestamp",
                    "user-nickname",
                    "user-active",
                    "permission-code",
                    "permission-name",
                    "pkg-name"
            });

            SelectQuery query = new SelectQuery(PermissionUserPkg.class);
            query.addOrdering(new Ordering(PermissionUserPkg.USER_PROPERTY + "." + User.NICKNAME_PROPERTY, SortOrder.ASCENDING));
            query.addOrdering(new Ordering(PermissionUserPkg.PERMISSION_PROPERTY + "." + Permission.CODE_PROPERTY, SortOrder.ASCENDING));
            query.setFetchLimit(LIMIT_GENERATECSV);
            List<PermissionUserPkg> rules;

            do {
                rules = context.performQuery(query);

                for (PermissionUserPkg rule : rules) {
                    writer.writeNext(new String[]{
                            dateTimeFormatter.format(Instant.ofEpochMilli(rule.getCreateTimestamp().getTime())),
                            rule.getUser().getNickname(),
                            Boolean.toString(rule.getUser().getActive()),
                            rule.getPermission().getCode(),
                            rule.getPermission().getName(),
                            null != rule.getPkg() ? rule.getPkg().getName() : ""
                    });
                }

                query.setFetchOffset(query.getFetchOffset() + LIMIT_GENERATECSV);
            }
            while (rules.size() >= LIMIT_GENERATECSV);

            writer.flush();
            outputStreamWriter.flush();
        }

    }

}
