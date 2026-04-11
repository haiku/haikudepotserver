/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.job.model.BulkDataJobCoordinatorService;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.support.ControllerHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.io.IOException;
import java.util.Optional;

@Controller
@RequestMapping(path = { "__pkg" })
public class PkgController {

    private static final Logger LOGGER = LoggerFactory.getLogger(PkgController.class);

    private final static String KEY_REPOSITORYSOURCECODE = "repositorySourceCode";
    private final static String KEY_NATURALLANGUAGECODE = "naturalLanguageCode";

    private final ServerRuntime serverRuntime;
    private final BulkDataJobCoordinatorService bulkDataJobCoordinatorService;
    private final JobService jobService;

    public PkgController(
            ServerRuntime serverRuntime,
            BulkDataJobCoordinatorService bulkDataJobCoordinatorService,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.bulkDataJobCoordinatorService = Preconditions.checkNotNull(bulkDataJobCoordinatorService);
        this.jobService = jobService;
    }

    /**
     * <p>This streams back a redirect to report data that contains a JSON stream gzip-compressed
     * that describes all of the packages for a repository source.  This is used by clients to get
     * deep(ish) data on all pkgs without having to query each one.</p>
     *
     * <p>The primary client for this is the Haiku desktop application &quot;Haiku Depot&quot;.
     * This API deprecates and older JSON-RPC API for obtaining bulk data.</p>
     */

    // TODO; observe the natural language code

    @RequestMapping(value = "/all-{repositorySourceCode}-{naturalLanguageCode}.json.gz", method = RequestMethod.GET)
    public void getAllAsJson(
            HttpServletResponse response,
            @PathVariable(value = KEY_NATURALLANGUAGECODE) String naturalLanguageCode,
            @PathVariable(value = KEY_REPOSITORYSOURCECODE) String repositorySourceCode,
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSinceHeader,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgentHeader)
            throws IOException {

        ObjectContext objectContext = serverRuntime.newContext();

        Optional<RepositorySource> repositorySourceOptional =
                RepositorySource.tryGetByCode(objectContext, repositorySourceCode);

        if (repositorySourceOptional.isEmpty()) {
            LOGGER.info("repository source [{}] not found", repositorySourceCode);
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } else {
            String jobCode = bulkDataJobCoordinatorService.getOrCreatePkgDumpExport(
                    NaturalLanguageCoordinates.fromCode(naturalLanguageCode),
                    repositorySourceCode
            );

            ControllerHelper.maybeRedirectToJobData(
                    jobService,
                    response,
                    jobCode,
                    ifModifiedSinceHeader,
                    userAgentHeader);
        }
    }

}
