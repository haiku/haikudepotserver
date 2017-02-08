/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.controller;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.RepositorySource;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.haiku.haikudepotserver.job.model.JobService;
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
@RequestMapping(path = {
        "repository", // TODO - remove
        "__repository"
})
public class RepositoryHpkrIngressController {

    protected static Logger LOGGER = LoggerFactory.getLogger(RepositoryHpkrIngressController.class);

    private final static String KEY_REPOSITORYCODE = "repositoryCode";
    private final static String KEY_REPOSITORYSOURCECODE = "repositorySourceCode";

    @Resource
    private JobService jobService;

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

        jobService.submit(
                new RepositoryHpkrIngressJobSpecification(repositoryCode),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

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

        jobService.submit(
                new RepositoryHpkrIngressJobSpecification(repositoryCode, Collections.singleton(repositorySourceCode)),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        return ResponseEntity.ok("repository source import submitted");
    }

}
