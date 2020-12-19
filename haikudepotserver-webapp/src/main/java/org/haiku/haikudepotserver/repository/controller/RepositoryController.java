/*
 * Copyright 2018-2020, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.controller;

import com.google.common.base.Preconditions;
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
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

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

@SuppressWarnings("unused") // spring setup
@Controller
@RequestMapping(path = RepositoryController.SEGMENT_REPOSITORY)
public class RepositoryController extends AbstractController {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryController.class);

    public final static String SEGMENT_REPOSITORY = "__repository";
    public final static String SEGMENT_IMPORT = "import";
    private final static String SEGMENT_SOURCE = "source";

    private final static String KEY_REPOSITORYCODE = "repositoryCode";
    private final static String KEY_REPOSITORYSOURCECODE = "repositorySourceCode";
    private final static String KEY_NATURALLANGUAGECODE = "naturalLanguageCode";

    private final ServerRuntime serverRuntime;
    private final JobService jobService;
    private final RepositoryService repositoryService;
    private final PermissionEvaluator permissionEvaluator;

    public RepositoryController(
            ServerRuntime serverRuntime,
            JobService jobService,
            RepositoryService repositoryService,
            PermissionEvaluator permissionEvaluator) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.jobService = Preconditions.checkNotNull(jobService);
        this.repositoryService = Preconditions.checkNotNull(repositoryService);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
    }

    /**
     * <p>This streams back a redirect to report data that contains a JSON stream gzip-compressed
     * that describes all of the repositories.  This is used by clients to get deep(ish) data on all
     * repositories without having to query each one.</p>
     */

    // TODO; observe the natural language code

    @RequestMapping(value = {
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

    /**
     * @deprecated to be replaced with POST.
     */

    // TODO; remove

    @Deprecated
    @RequestMapping(value = "{" + KEY_REPOSITORYCODE + "}/" + SEGMENT_IMPORT,  method = RequestMethod.GET)
    public ResponseEntity<String> importRepositoryGet(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode) {
        return importRepository(repositoryCode);
    }

    /**
     * <p>Instructs HDS to start importing data for all repository sources of
     * the nominated repository</p>
     */

    @RequestMapping(value = "{" + KEY_REPOSITORYCODE + "}/" + SEGMENT_IMPORT,  method = RequestMethod.POST)
    public ResponseEntity<String> importRepository(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode) {

        ObjectContext context = serverRuntime.newContext();
        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, repositoryCode);

        if (repositoryOptional.isEmpty()) {
            return new ResponseEntity<>("repository not found", HttpStatus.NOT_FOUND);
        }

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositoryOptional.get(),
                Permission.REPOSITORY_IMPORT)) {
            throw new AccessDeniedException("unable to import repository [" + repositoryOptional.get() + "]");
        }

        jobService.submit(
                new RepositoryHpkrIngressJobSpecification(repositoryCode),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        return ResponseEntity.ok("repository import submitted");
    }

    /**
     * @deprecated replaced with a POST equivalent.
     */

    // TODO; remove

    @Deprecated
    @RequestMapping(
            value = "{"+KEY_REPOSITORYCODE+"}/" + SEGMENT_SOURCE + "/{"+KEY_REPOSITORYSOURCECODE+"}/" + SEGMENT_IMPORT,
            method = RequestMethod.GET)
    public ResponseEntity<String> importRepositorySourceGet(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode,
            @PathVariable(value = KEY_REPOSITORYSOURCECODE) String repositorySourceCode) {
        return importRepositorySource(repositoryCode, repositorySourceCode);
    }

    /**
     * <p>Instructs HDS to import repository data for a repository source of
     * a repository.</p>
     */

    @RequestMapping(
            value = "{"+KEY_REPOSITORYCODE+"}/" + SEGMENT_SOURCE + "/{"+KEY_REPOSITORYSOURCECODE+"}/" + SEGMENT_IMPORT,
            method = RequestMethod.POST)
    public ResponseEntity<String> importRepositorySource(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode,
            @PathVariable(value = KEY_REPOSITORYSOURCECODE) String repositorySourceCode) {

        ObjectContext context = serverRuntime.newContext();
        Optional<Repository> repositoryOptional = Repository.tryGetByCode(context, repositoryCode);

        if (repositoryOptional.isEmpty()) {
            return new ResponseEntity<>("repository not found", HttpStatus.NOT_FOUND);
        }

        Optional<RepositorySource> repositorySourceOptional = RepositorySource
                .tryGetByCode(context, repositorySourceCode);

        if(
                repositorySourceOptional.isEmpty()
                        || !repositoryOptional.get().equals(repositorySourceOptional.get().getRepository())) {
            return new ResponseEntity<>("repository source not found", HttpStatus.NOT_FOUND);
        }

        if (!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                repositoryOptional.get(),
                Permission.REPOSITORY_IMPORT)) {
            throw new AccessDeniedException("unable to import repository [" + repositoryOptional.get() + "]");
        }

        jobService.submit(
                new RepositoryHpkrIngressJobSpecification(repositoryCode, Collections.singleton(repositorySourceCode)),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        return ResponseEntity.ok("repository source import submitted");
    }

}
