/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.user.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

/**
 * <p>This object models a job that can be submitted to synchronize the user data into an LDAP directory.</p>
 */

public class LdapSynchronizeUsersJobSpecification extends AbstractJobSpecification {

    @Override
    public Long getTimeToLive() {
        return 120 * 1000L; // only stay around for a short while
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        return LdapSynchronizeUsersJobSpecification.class.isAssignableFrom(other.getClass());
    }

}
