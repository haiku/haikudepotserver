/*
 * Copyright 2013-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.controller;

import com.google.common.net.HttpHeaders;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryService;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import java.util.Optional;

/**
 * <p>This is the HTTP endpoint from which external systems are able to trigger a repository to be scanned for
 * new packages by fetching the HPKR file and processing it.  The actual logistics in this controller do not use
 * typical Spring MVC error handling and so on; this is because fine control is required and this seems to be
 * an easy way to achieve that; basically done manually.</p>
 */

@Controller
@RequestMapping(path = {
        "repository", // TODO - remove
        "__repository"
})
public class RepositoryController extends AbstractController {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryController.class);

    private final static String SEGMENT_IMPORT = "import";
    private final static String SEGMENT_SOURCE = "source";

    private final static String KEY_REPOSITORYCODE = "repositoryCode";
    private final static String KEY_REPOSITORYSOURCECODE = "repositorySourceCode";
    private final static String KEY_NATURALLANGUAGECODE = "naturalLanguageCode";

    @Resource
    private JobService jobService;

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private RepositoryService repositoryService;

    /**
     * <p>This streams back a redirect to report data that contains a JSON stream gzip-compressed
     * that describes all of the repositories.  This is used by clients to get deep(ish) data on all
     * repositories without having to query each one.</p>
     */

    // TODO; observe the natural language code

    @RequestMapping(value = {
            "/all_{naturalLanguageCode}.json.gz", // deprecated
            "/all-{naturalLanguageCode}.json.gz"
    }, method = RequestMethod.GET)
    public void getAllAsJson(
            HttpServletResponse response,
            @PathVariable(value = KEY_NATURALLANGUAGECODE) String naturalLanguageCode,
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSinceHeader)
        throws IOException {

        JobController.handleRedirectToJobData(
                response,
                jobService,
                ifModifiedSinceHeader,
                repositoryService.getLastRepositoryModifyTimestampSecondAccuracy(serverRuntime.newContext()),
                new RepositoryDumpExportJobSpecification());
    }

    @RequestMapping(value = "{"+KEY_REPOSITORYCODE+"}/" + SEGMENT_IMPORT,  method = RequestMethod.GET)
    public ResponseEntity<String> fetchRepository(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode) {

        ObjectContext context = serverRuntime.newContext();
        Optional<Repository> repositoryOptional = Repository.getByCode(context, repositoryCode);

        if(!repositoryOptional.isPresent()) {
            return new ResponseEntity<>("repository not found", HttpStatus.NOT_FOUND);
        }

        jobService.submit(
                new RepositoryHpkrIngressJobSpecification(repositoryCode),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        return ResponseEntity.ok("repository import submitted");

    }

    @RequestMapping(
            value = "{"+KEY_REPOSITORYCODE+"}/" + SEGMENT_SOURCE + "/{"+KEY_REPOSITORYSOURCECODE+"}/" + SEGMENT_IMPORT,
            method = RequestMethod.GET)
    public ResponseEntity<String> fetchRepository(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode,
            @PathVariable(value = KEY_REPOSITORYSOURCECODE) String repositorySourceCode) {

        ObjectContext context = serverRuntime.newContext();
        Optional<Repository> repositoryOptional = Repository.getByCode(context, repositoryCode);

        if(!repositoryOptional.isPresent()) {
            return new ResponseEntity<>("repository not found", HttpStatus.NOT_FOUND);
        }

        Optional<RepositorySource> repositorySourceOptional = RepositorySource.getByCode(context, repositorySourceCode);

        if(
                !repositorySourceOptional.isPresent()
                        || !repositoryOptional.get().equals(repositorySourceOptional.get().getRepository())) {
            return new ResponseEntity<>("repository source not found", HttpStatus.NOT_FOUND);
        }

        jobService.submit(
                new RepositoryHpkrIngressJobSpecification(repositoryCode, Collections.singleton(repositorySourceCode)),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        return ResponseEntity.ok("repository source import submitted");
    }

}
