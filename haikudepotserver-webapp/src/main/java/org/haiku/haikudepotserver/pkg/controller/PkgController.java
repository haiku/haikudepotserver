/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.controller;

import com.google.common.net.HttpHeaders;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.pkg.model.PkgDumpExportJobSpecification;
import org.haiku.haikudepotserver.pkg.model.PkgService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Date;
import java.util.Optional;

@Controller
@RequestMapping(path = { "__pkg" })
public class PkgController {

    protected static Logger LOGGER = LoggerFactory.getLogger(PkgController.class);

    private final static String KEY_REPOSITORYSOURCECODE = "repositorySourceCode";
    private final static String KEY_NATURALLANGUAGECODE = "naturalLanguageCode";

    @Resource
    private JobService jobService;

    @Resource
    private PkgService pkgService;

    @Resource
    private ServerRuntime serverRuntime;

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
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSinceHeader)
            throws IOException {

        ObjectContext objectContext = serverRuntime.newContext();

        Optional<RepositorySource> repositorySourceOptional =
                RepositorySource.getByCode(objectContext, repositorySourceCode);

        if(!repositorySourceOptional.isPresent()) {
            LOGGER.info("repository source [" + repositorySourceCode + "] not found");
            response.setStatus(HttpStatus.NOT_FOUND.value());
        } else {
            Date lastModifiedTimestamp = pkgService.getLastModifyTimestampSecondAccuracy(
                    objectContext, repositorySourceOptional.get());
            PkgDumpExportJobSpecification specification = new PkgDumpExportJobSpecification();
            specification.setNaturalLanguageCode(naturalLanguageCode);
            specification.setRepositorySourceCode(repositorySourceCode);

            JobController.handleRedirectToJobData(
                    response,
                    jobService,
                    ifModifiedSinceHeader,
                    lastModifiedTimestamp,
                    specification);
        }
    }

}
