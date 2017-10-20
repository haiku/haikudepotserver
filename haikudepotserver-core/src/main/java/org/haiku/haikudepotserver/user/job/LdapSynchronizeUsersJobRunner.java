/*
 * Copyright 2014-2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.user.job;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.user.LdapException;
import org.haiku.haikudepotserver.user.LdapSynchronizationService;
import org.haiku.haikudepotserver.user.model.LdapSynchronizeUsersJobSpecification;
import org.haiku.haikudepotserver.user.model.UserService;
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
    private ServerRuntime serverRuntime;

    @Resource
    private UserService userService;

    @Resource
    private LdapSynchronizationService ldapSynchronizationService;

    @Override
    public void run(JobService jobService, LdapSynchronizeUsersJobSpecification job) {
        Preconditions.checkNotNull(job);

        try {
            ldapSynchronizationService.ldapSynchronizeAllUsers(serverRuntime.newContext());
            ldapSynchronizationService.ldapRemoveNonExistentUsers(serverRuntime.newContext());
        }
        catch(LdapException le) {
            LOGGER.error("unable to ldap synchronize users", le);
        }
    }

}
