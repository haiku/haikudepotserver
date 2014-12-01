/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.support.job.AbstractJobRunner;
import org.haikuos.haikudepotserver.user.model.LdapSynchronizeUsersJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

/**
 * <p>This background service will asynchronously go through the users and will relay the data for the users
 * into an LDAP server.</p>
 */

public class LdapSynchronizeUsersJobRunner
        extends AbstractJobRunner<LdapSynchronizeUsersJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(LdapSynchronizeUsersJobRunner.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    UserOrchestrationService userOrchestrationService;

    @Override
    public void run(LdapSynchronizeUsersJobSpecification job) {
        Preconditions.checkNotNull(job);

        try {
            userOrchestrationService.ldapSynchronizeAllUsers(serverRuntime.getContext());
            userOrchestrationService.ldapRemoveNonExistentUsers(serverRuntime.getContext());
        }
        catch(LdapException le) {
            LOGGER.error("unable to ldap synchronize users", le);
        }
    }

}
