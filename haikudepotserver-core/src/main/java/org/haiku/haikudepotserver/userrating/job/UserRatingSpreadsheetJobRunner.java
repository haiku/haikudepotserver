/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.job;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSink;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.support.DateTimeHelper;
import org.haiku.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingService;
import org.haiku.haikudepotserver.userrating.model.UserRatingSpreadsheetJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
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

    private final ServerRuntime serverRuntime;
    private final UserRatingService userRatingService;

    public UserRatingSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            UserRatingService userRatingService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.userRatingService = Preconditions.checkNotNull(userRatingService);
    }

    @Override
    public void run(JobService jobService, UserRatingSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
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

            Optional<Pkg> paramPkgOptional = Optional.empty();
            Optional<User> paramUserOptional = Optional.empty();
            Optional<Repository> paramRepositoryOptional = Optional.empty();

            if (!Strings.isNullOrEmpty(specification.getRepositoryCode())) {
                paramRepositoryOptional = Repository.tryGetByCode(context, specification.getRepositoryCode());

                if(!paramRepositoryOptional.isPresent()) {
                    throw new IllegalStateException("unable to find the repository; " + specification.getRepositoryCode());
                }
            }

            if (!Strings.isNullOrEmpty(specification.getUserNickname())) {
                paramUserOptional = User.tryGetByNickname(context, specification.getUserNickname());

                if(!paramUserOptional.isPresent()) {
                    throw new IllegalStateException("unable to find the user; " + specification.getUserNickname());
                }
            }

            if (!Strings.isNullOrEmpty(specification.getPkgName())) {
                paramPkgOptional = Pkg.tryGetByName(context, specification.getPkgName());

                if (!paramPkgOptional.isPresent()) {
                    throw new IllegalStateException("unable to find the package; " + specification.getPkgName());
                }
            }

            writer.writeNext(new String[]{
                    "pkg-name",
                    "repository-code",
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

            final DateTimeFormatter dateTimeFormatter = DateTimeHelper.createStandardDateTimeFormat();

            UserRatingSearchSpecification spec = new UserRatingSearchSpecification();
            spec.setPkg(paramPkgOptional.orElse(null));
            spec.setUser(paramUserOptional.orElse(null));
            spec.setRepository(paramRepositoryOptional.orElse(null));

            // TODO; provide a prefetch tree into the user, pkgversion.
            int count = userRatingService.each(context, spec, userRating -> {

                writer.writeNext(
                        new String[]{
                                userRating.getPkgVersion().getPkg().getName(),
                                userRating.getPkgVersion().getRepositorySource().getRepository().getCode(),
                                userRating.getPkgVersion().getArchitecture().getCode(),
                                userRating.getPkgVersion().toVersionCoordinates().toString(),
                                userRating.getUser().getNickname(),
                                dateTimeFormatter.format(Instant.ofEpochMilli(userRating.getCreateTimestamp().getTime())),
                                dateTimeFormatter.format(Instant.ofEpochMilli(userRating.getModifyTimestamp().getTime())),
                                null != userRating.getRating() ? userRating.getRating().toString() : "",
                                null != userRating.getUserRatingStability() ? userRating.getUserRatingStability().getCode() : "",
                                userRating.getNaturalLanguage().getCode(),
                                userRating.getComment(),
                                userRating.getCode()
                        }
                );

                return true;
            });

            LOGGER.info(
                    "did produce user rating spreadsheet report for {} user ratings in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }
}
