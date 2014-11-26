/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.controller;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Optional;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.Callback;
import org.haikuos.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

/**
 * <p>This report generates a report that lists the prominence of the packages.</p>
 */

@Controller
@RequestMapping("/secured/pkg/pkgprominenceanduserratingspreadsheetreport")
public class PkgProminenceAndUserRatingSpreadsheetReportController extends AbstractController {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgProminenceAndUserRatingSpreadsheetReportController.class);

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private PkgOrchestrationService pkgOrchestrationService;

    @RequestMapping(value="download.csv", method = RequestMethod.GET)
    public void generate(HttpServletResponse response) throws IOException {

        ObjectContext context = serverRuntime.getContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!authorizationService.check(
                context,
                user.orNull(),
                null, Permission.BULK_PKGPROMINENCEANDUSERRATINGSPREADSHEETREPORT)) {
            LOGGER.warn("attempt to access a prominence and user rating coverage spreadsheet report, but was unauthorized");
            throw new AuthorizationFailure();
        }

        setHeadersForCsvDownload(response, "pkgprominenceanduserratingspreadsheetreport");

        final CSVWriter writer = new CSVWriter(response.getWriter(), ',');

        writer.writeNext(new String[] {
                "pkg-name",
                "prominence-name",
                "prominence-ordering",
                "derived-rating",
                "derived-rating-sample-size"
        });

        // stream out the packages.

        long startMs = System.currentTimeMillis();
        LOGGER.info("will produce prominence spreadsheet report");

        int count = pkgOrchestrationService.each(
                context,
                null,
                Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE)),
                new Callback<Pkg>() {
                    @Override
                    public boolean process(Pkg pkg) {

                        writer.writeNext(
                                new String[] {
                                        pkg.getName(),
                                        pkg.getProminence().getName(),
                                        pkg.getProminence().getOrdering().toString(),
                                        null==pkg.getDerivedRating() ? "" : pkg.getDerivedRating().toString(),
                                        null==pkg.getDerivedRating() ? "" : pkg.getDerivedRatingSampleSize().toString()
                                }
                        );

                        return true;
                    }
                });

        LOGGER.info(
                "did produce prominence spreadsheet report for {} packages in {}ms",
                count,
                System.currentTimeMillis() - startMs);

        writer.close();

    }

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="the authenticated user is not able to run the package prominence spreadsheet report")
    public class AuthorizationFailure extends RuntimeException {}

}
