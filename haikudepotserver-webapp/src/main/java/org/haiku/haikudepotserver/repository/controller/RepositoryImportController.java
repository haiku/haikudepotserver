/*
 * Copyright 2013-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.controller;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.repository.model.PkgRepositoryImportJobSpecification;
import org.haiku.haikudepotserver.job.JobOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Optional;

/**
 * <p>This is the HTTP endpoint from which external systems are able to trigger a repository to be scanned for
 * new packages by fetching the HPKR file and processing it.  The actual logistics in this controller do not use
 * typical Spring MVC error handling and so on; this is because fine control is required and this seems to be
 * an easy way to achieve that; basically done manually.</p>
 */

@Controller
@RequestMapping(path = { "/repository", "/__repository" })
public class RepositoryImportController {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryImportController.class);

    public final static String KEY_REPOSITORYCODE = "repositoryCode";
    public final static String KEY_REPOSITORYSOURCECODE = "repositorySourceCode";

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Resource
    private ServerRuntime serverRuntime;

    @RequestMapping(value = "{"+KEY_REPOSITORYCODE+"}/import",  method = RequestMethod.GET)
    public ResponseEntity<String> fetchRepository(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode) {

        ObjectContext context = serverRuntime.getContext();
        Optional<Repository> repositoryOptional = Repository.getByCode(context, repositoryCode);

        if(!repositoryOptional.isPresent()) {
            return new ResponseEntity<>("repository not found", HttpStatus.NOT_FOUND);
        }

        jobOrchestrationService.submit(
                new PkgRepositoryImportJobSpecification(repositoryCode),
                JobOrchestrationService.CoalesceMode.QUEUED);

        return ResponseEntity.ok("repository import submitted");

    }

    @RequestMapping(value = "{"+KEY_REPOSITORYCODE+"}/source/{"+KEY_REPOSITORYSOURCECODE+"}/import",  method = RequestMethod.GET)
    public ResponseEntity<String> fetchRepository(
            @PathVariable(value = KEY_REPOSITORYCODE) String repositoryCode,
            @PathVariable(value = KEY_REPOSITORYSOURCECODE) String repositorySourceCode) {

        ObjectContext context = serverRuntime.getContext();
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

        jobOrchestrationService.submit(
                new PkgRepositoryImportJobSpecification(repositoryCode, Collections.singleton(repositorySourceCode)),
                JobOrchestrationService.CoalesceMode.QUEUED);

        return ResponseEntity.ok("repository source import submitted");
    }

}
