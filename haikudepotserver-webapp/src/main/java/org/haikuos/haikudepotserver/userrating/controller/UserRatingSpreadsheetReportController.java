/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating.controller;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Optional;
import com.google.common.base.Strings;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.dataobjects.UserRating;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.Callback;
import org.haikuos.haikudepotserver.support.web.AbstractController;
import org.haikuos.haikudepotserver.userrating.UserRatingOrchestrationService;
import org.haikuos.haikudepotserver.userrating.model.UserRatingSearchSpecification;
import org.joda.time.format.DateTimeFormatter;
import org.joda.time.format.ISODateTimeFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>This controller is able to produce a spreadsheet of user rating data.  It is able to
 * produce this for a number of qualifiers.  For example, it is able to produce a report
 * for a package, a report for a user and is also able to produce a dump of all user
 * ratings in the whole system.</p>
 */

@Controller
@RequestMapping("/secured/userrating/userratingspreadsheetreport")
public class UserRatingSpreadsheetReportController extends AbstractController {

    private static Logger LOGGER = LoggerFactory.getLogger(UserRatingSpreadsheetReportController.class);

    private final static String KEY_USERNICKNAME = "nicknam";

    private final static String KEY_PKGNAME = "pkgname";

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private UserRatingOrchestrationService userRatingOrchestrationService;

    @RequestMapping(value="download.csv", method = RequestMethod.GET)
    public void generate(
            HttpServletResponse response,
            @RequestParam(value = KEY_USERNICKNAME, required = false) String paramUserNickname,
            @RequestParam(value = KEY_PKGNAME, required = false) String paramPkgName
            ) throws IOException {

        ObjectContext context = serverRuntime.getContext();

        StringBuilder filenameIdentifier = new StringBuilder("userratingspreadsheetreport");
        Optional<User> user = tryObtainAuthenticatedUser(context);
        Optional<Pkg> paramPkgOptional = Optional.absent();
        Optional<User> paramUserOptional = Optional.absent();

        if(!Strings.isNullOrEmpty(paramUserNickname)) {
            paramUserOptional = User.getByNickname(context, paramUserNickname);

            if(!paramUserOptional.isPresent()) {
                LOGGER.warn("attempt to produce user rating report for user {}, but that user does not exist -- not allowed", paramUserNickname);
                throw new AuthorizationFailure();
            }

            if(!authorizationService.check(
                    context,
                    user.orNull(),
                    paramUserOptional.get(),
                    Permission.BULK_USERRATINGSPREADSHEETREPORT_USER)) {
                LOGGER.warn("attempt to access a user rating report for user {}, but this was disallowed", paramUserNickname);
                throw new AuthorizationFailure();
            }

            filenameIdentifier.append("_user-");
            filenameIdentifier.append(paramUserNickname);
        }

        if(!Strings.isNullOrEmpty(paramPkgName)) {
            paramPkgOptional = Pkg.getByName(context, paramPkgName);

            if(!paramPkgOptional.isPresent()) {
                LOGGER.warn("attempt to produce user rating report for pkg {}, but that pkg does not exist -- not allowed", paramPkgName);
                throw new AuthorizationFailure();
            }

            if(!authorizationService.check(
                    context,
                    user.orNull(),
                    paramPkgOptional.get(),
                    Permission.BULK_USERRATINGSPREADSHEETREPORT_PKG)) {
                LOGGER.warn("attempt to access a user rating report for pkg {}, but this was disallowed", paramUserNickname);
                throw new AuthorizationFailure();
            }

            filenameIdentifier.append("_pkg-");
            filenameIdentifier.append(paramPkgName);
        }

        // if there is no user and no package supplied then assume that
        // the user is trying to produce a report for all user ratings.

        if(!paramPkgOptional.isPresent() && !paramUserOptional.isPresent()) {
            if(!authorizationService.check(
                    context,
                    user.orNull(),
                    null,
                    Permission.BULK_USERRATINGSPREADSHEETREPORT_ALL)) {
                LOGGER.warn("attempt to access a user rating report for all, but this was disallowed");
                throw new AuthorizationFailure();
            }

            filenameIdentifier.append("_all");
        }

        setHeadersForCsvDownload(response, filenameIdentifier.toString());

        final CSVWriter writer = new CSVWriter(response.getWriter(), ',');

        writer.writeNext(new String[] {
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
        spec.setPkg(paramPkgOptional.orNull());
        spec.setUser(paramUserOptional.orNull());

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

        writer.close();

    }

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="the authenticated user is not able to run the user rating spreadsheet report")
    public class AuthorizationFailure extends RuntimeException {}

}
