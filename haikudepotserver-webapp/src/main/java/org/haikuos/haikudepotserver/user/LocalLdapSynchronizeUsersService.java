/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user;

import com.google.common.base.Preconditions;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.support.AbstractLocalBackgroundProcessingService;
import org.haikuos.haikudepotserver.user.model.LdapSynchronizeUsersJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

/**
 * <p>This background service will asynchronously go through the users and will relay the data for the users
 * into an LDAP server.</p>
 */

public class LocalLdapSynchronizeUsersService
        extends AbstractLocalBackgroundProcessingService
        implements LdapSynchronizeUsersService {

    protected static Logger LOGGER = LoggerFactory.getLogger(LocalLdapSynchronizeUsersService.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    UserOrchestrationService userOrchestrationService;

    @Override
    public void submit(LdapSynchronizeUsersJob job) {
        Preconditions.checkNotNull(job);
        Preconditions.checkState(null != executor, "the service is not running, but a job is being submitted");
        executor.submit(new LdapUpdateUsersJobRunnable(this, job));
        LOGGER.info("did submit job to update users in ldap directory");
    }

    protected void run(LdapSynchronizeUsersJob job) {
        Preconditions.checkNotNull(job);

        try {
            userOrchestrationService.ldapSynchronizeAllUsers(serverRuntime.getContext());
            userOrchestrationService.ldapRemoveNonExistentUsers(serverRuntime.getContext());
        }
        catch(LdapException le) {
            LOGGER.error("unable to ldap synchronize users", le);
        }
    }

    public static class LdapUpdateUsersJobRunnable implements Runnable {

        private LdapSynchronizeUsersJob job;

        private LocalLdapSynchronizeUsersService service;

        public LdapUpdateUsersJobRunnable(
                LocalLdapSynchronizeUsersService service,
                LdapSynchronizeUsersJob job) {
            Preconditions.checkNotNull(service);
            Preconditions.checkNotNull(job);
            this.service = service;
            this.job = job;
        }

        public LdapSynchronizeUsersJob getJob() {
            return job;
        }

        @Override
        public void run() {
            service.run(job);
        }

    }

}
