/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.job;

import com.google.common.base.Preconditions;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobDataEncoding;
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
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
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

    private final static Logger LOGGER = LoggerFactory.getLogger(UserRatingSpreadsheetJobRunner.class);

    private final static String[] HEADERS = new String[]{
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
    };

    private final ServerRuntime serverRuntime;
    private final UserRatingService userRatingService;

    public UserRatingSpreadsheetJobRunner(
            ServerRuntime serverRuntime,
            UserRatingService userRatingService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.userRatingService = Preconditions.checkNotNull(userRatingService);
    }

    @Override
    public Class<UserRatingSpreadsheetJobSpecification> getSupportedSpecificationClass() {
        return UserRatingSpreadsheetJobSpecification.class;
    }

    @Override
    public void run(JobService jobService, UserRatingSpreadsheetJobSpecification specification) throws IOException {

        Preconditions.checkArgument(null != jobService);
        Preconditions.checkArgument(null!=specification);

        final ObjectContext context = serverRuntime.newContext();

        final CSVFormat format = CSVFormat.DEFAULT.builder()
                .setHeader(HEADERS)
                .get();

        // this will register the outbound data against the job.
        JobDataWithByteSink jobDataWithByteSink = jobService.storeGeneratedData(
                specification.getGuid(),
                "download",
                MediaType.CSV_UTF_8.toString(),
                JobDataEncoding.NONE);

        try(
                final OutputStream outputStream = jobDataWithByteSink.getByteSink().openBufferedStream();
                final OutputStreamWriter outputStreamWriter = new OutputStreamWriter(outputStream);
                final CSVPrinter printer = new CSVPrinter(outputStreamWriter, format)
        ) {

            Pkg pkg = Optional.ofNullable(specification.getPkgName())
                    .map(StringUtils::trimToNull)
                    .map(n -> Pkg.getByName(context, n))
                    .orElse(null);

            User user = Optional.ofNullable(specification.getUserNickname())
                    .map(StringUtils::trimToNull)
                    .map(n -> User.getByNickname(context, n))
                    .orElse(null);

            Repository repository = Optional.ofNullable(specification.getRepositoryCode())
                    .map(StringUtils::trimToNull)
                    .map(c -> Repository.getByCode(context, c))
                    .orElse(null);

            // stream out the packages.

            long startMs = System.currentTimeMillis();
            LOGGER.info("will produce user rating spreadsheet report");

            final DateTimeFormatter dateTimeFormatter = DateTimeHelper.createStandardDateTimeFormat();

            UserRatingSearchSpecification spec = new UserRatingSearchSpecification();
            spec.setPkg(pkg);
            spec.setUser(user);
            spec.setRepository(repository);

            String[] row = new String[HEADERS.length];

            // TODO; provide a prefetch tree into the user, pkgversion.
            int count = userRatingService.each(context, spec, userRating -> {
                row[0] = userRating.getPkgVersion().getPkg().getName();
                row[1] = userRating.getPkgVersion().getRepositorySource().getRepository().getCode();
                row[2] = userRating.getPkgVersion().getArchitecture().getCode();
                row[3] = userRating.getPkgVersion().toVersionCoordinates().toString();
                row[4] = userRating.getUser().getNickname();
                row[5] = dateTimeFormatter.format(Instant.ofEpochMilli(userRating.getCreateTimestamp().getTime()));
                row[6] = dateTimeFormatter.format(Instant.ofEpochMilli(userRating.getModifyTimestamp().getTime()));
                row[7] = null != userRating.getRating() ? userRating.getRating().toString() : "";
                row[8] = null != userRating.getUserRatingStability() ? userRating.getUserRatingStability().getCode() : "";
                row[9] = userRating.getNaturalLanguage().getCode();
                row[10] = userRating.getComment();
                row[11] = userRating.getCode();

                try {
                    printer.printRecord(Arrays.stream(row));
                } catch (IOException ioe) {
                    throw new UncheckedIOException("unable to write row", ioe);
                }

                return true;
            });

            printer.flush();
            outputStreamWriter.flush();

            LOGGER.info(
                    "did produce user rating spreadsheet report for {} user ratings in {}ms",
                    count,
                    System.currentTimeMillis() - startMs);
        }

    }
}
