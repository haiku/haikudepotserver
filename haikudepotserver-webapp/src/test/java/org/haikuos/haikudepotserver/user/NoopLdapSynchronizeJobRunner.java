/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

import org.haikuos.haikudepotserver.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.user.model.LdapSynchronizeUsersJobSpecification;

/**
 * <P>No-operation implementation of this service for testing purposes.</P>
 */

public class NoopLdapSynchronizeJobRunner extends AbstractJobRunner<LdapSynchronizeUsersJobSpecification> {

    @Override
    public String getJobTypeCode() {
        return "ldapsynchronizeusers";
    }

    @Override
    public void run(JobOrchestrationService jobOrchestrationService, LdapSynchronizeUsersJobSpecification job) {
        // do nothing
    }

}
