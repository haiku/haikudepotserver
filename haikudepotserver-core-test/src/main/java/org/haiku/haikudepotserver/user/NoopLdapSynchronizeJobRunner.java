/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.user;

import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.user.model.LdapSynchronizeUsersJobSpecification;
import org.haiku.haikudepotserver.job.JobOrchestrationService;

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
