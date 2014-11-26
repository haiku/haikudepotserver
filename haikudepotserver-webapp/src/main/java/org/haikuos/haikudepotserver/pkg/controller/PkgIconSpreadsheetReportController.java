/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.pkg.controller;

import au.com.bytecode.opencsv.CSVWriter;
import com.google.common.base.Optional;
import com.google.common.collect.Lists;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.query.PrefetchTreeNode;
import org.haikuos.haikudepotserver.dataobjects.Architecture;
import org.haikuos.haikudepotserver.dataobjects.Pkg;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.pkg.PkgOrchestrationService;
import org.haikuos.haikudepotserver.pkg.model.PkgIconConfiguration;
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
import java.util.List;

/**
 * <p>This report produces a list of icon configuration by package.</p>
 */

@Controller
@RequestMapping("/secured/pkg/pkgiconspreadsheetreport")
public class PkgIconSpreadsheetReportController extends AbstractController {

    private static Logger LOGGER = LoggerFactory.getLogger(PkgIconSpreadsheetReportController.class);

    private static String MARKER = "*";

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
                null, Permission.BULK_PKGICONSPREADSHEETREPORT)) {
            LOGGER.warn("attempt to access a package icon spreadsheet report, but was unauthorized");
            throw new AuthorizationFailure();
        }

        setHeadersForCsvDownload(response, "pkgiconspreadsheetreport");

        final CSVWriter writer = new CSVWriter(response.getWriter(), ',');

        // obtain a list of all of the possible media configurations

        final List<PkgIconConfiguration> pkgIconConfigurations = pkgOrchestrationService.getInUsePkgIconConfigurations(context);

        {
            List<String> headings = Lists.newArrayList();

            headings.add("pkg-name");
            headings.add("no-icons");

            for (PkgIconConfiguration pkgIconConfiguration : pkgIconConfigurations) {

                StringBuilder heading = new StringBuilder();

                heading.append(pkgIconConfiguration.getMediaType().getCode());

                if (null != pkgIconConfiguration.getSize()) {
                    heading.append("@");
                    heading.append(pkgIconConfiguration.getSize().toString());
                }

                headings.add(heading.toString());

            }

            writer.writeNext(headings.toArray(new String[headings.size()]));
        }

        // stream out the packages.

        long startMs = System.currentTimeMillis();
        LOGGER.info("will produce icon spreadsheet report");

        PrefetchTreeNode prefetchTreeNode = new PrefetchTreeNode();
        prefetchTreeNode.addPath(Pkg.PKG_ICONS_PROPERTY);

        int count = pkgOrchestrationService.each(
                context,
                prefetchTreeNode,
                Architecture.getAllExceptByCode(context, Collections.singleton(Architecture.CODE_SOURCE)),
                new Callback<Pkg>() {
                    @Override
                    public boolean process(Pkg pkg) {

                        List<String> cells = Lists.newArrayList();
                        cells.add(pkg.getName());

                        cells.add(pkg.getPkgIcons().isEmpty() ? MARKER : "");

                        for(PkgIconConfiguration pkgIconConfiguration : pkgIconConfigurations) {
                            cells.add(
                                    pkg.getPkgIcon(
                                            pkgIconConfiguration.getMediaType(),
                                            pkgIconConfiguration.getSize()
                                    ).isPresent()
                                            ? MARKER
                                            : "");
                        }

                        writer.writeNext(cells.toArray(new String[cells.size()]));

                        return true;
                    }
                });

        LOGGER.info(
                "did produce icon report for {} packages in {}ms",
                count,
                System.currentTimeMillis() - startMs);

        writer.close();

    }

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="the authenticated user is not able to run the package icon spreadsheet report")
    public class AuthorizationFailure extends RuntimeException {}


}
