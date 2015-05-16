/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSink;
import org.haikuos.haikudepotserver.support.Callback;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSpreadsheetJobSpecification;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.Optional;

/**
 * <p>This runner produces a spreadsheet containing details of the user ratings.  It can produce with the following
 * constraints;</p>
 *
 * <ul>
 *     <li>All user ratings</li>
 *     <li>All user ratings created by a given user</li>
 *     <li>All user ratings related to a specific package</li>
 * </ul>
 */

@Component
public class UserRatingSpreadsheetJobRunner extends AbstractJobRunner<UserRatingSpreadsheetJobSpecification> {

    private static Logger LOGGER = LoggerFactory.getLogger(UserRatingSpreadsheetJobRunner.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private UserRatingOrchestrationService userRatingOrchestrationService;

    @Override
    public void run(JobOrchestrationService jobOrchestrationService, UserRatingSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobOrchestrationService);
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

            Optional<Pkg> paramPkgOptional = Optional.empty();
            Optional<User> paramUserOptional = Optional.empty();

            if (!Strings.isNullOrEmpty(specification.getUserNickname())) {
                paramUserOptional = User.getByNickname(context, specification.getUserNickname());

                if(!paramUserOptional.isPresent()) {
                    throw new IllegalStateException("unable to find the user; " + specification.getUserNickname());
                }
            }

            if (!Strings.isNullOrEmpty(specification.getPkgName())) {
                paramPkgOptional = Pkg.getByName(context, specification.getPkgName());

                if (!paramPkgOptional.isPresent()) {
                    throw new IllegalStateException("unable to find the package; " + specification.getPkgName());
                }
            }

            writer.writeNext(new String[]{
                    "pkg-name",
                    "architecture-code",
                    "version-coordinates",
                    "user-nickname",
                    "create-timestamp",
                    "modify-timestamp",
                    "rating",
                    "stability-code",
                    "natural-language-code",
                    "comment",
                    "code"
            });

            // stream out the packages.

            long startMs = System.currentTimeMillis();
            LOGGER.info("will user rating spreadsheet report");

            final DateTimeFormatter dateTimeFormatter = ISODateTimeFormat.basicDateTimeNoMillis();

            UserRatingSearchSpecification spec = new UserRatingSearchSpecification();
            spec.setPkg(paramPkgOptional.orElse(null));
            spec.setUser(paramUserOptional.orElse(null));

            // TODO; provide a prefetch tree into the user, pkgversion.
            int count = userRatingOrchestrationService.each(context, spec, new Callback<UserRating>() {
                @Override
                public boolean process(UserRating userRating) {

                    writer.writeNext(
                            new String[]{
                                    userRating.getPkgVersion().getPkg().getName(),
                                    userRating.getPkgVersion().getArchitecture().getCode(),
                                    userRating.getPkgVersion().toVersionCoordinates().toString(),
                                    userRating.getUser().getNickname(),
                                    dateTimeFormatter.print(userRating.getCreateTimestamp().getTime()),
                                    dateTimeFormatter.print(userRating.getModifyTimestamp().getTime()),
                                    userRating.getRating().toString(),
                                    null != userRating.getUserRatingStability() ? userRating.getUserRatingStability().getCode() : "",
                                    userRating.getNaturalLanguage().getCode(),
                                    userRating.getComment(),
                                    userRating.getCode()
                            }
                    );

                    return true;
                }
            });

            LOGGER.info(
                    "did produce user rating spreadsheet report for {} user ratings in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }
}
