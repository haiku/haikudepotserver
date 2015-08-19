/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

public class PasswordResetMaintenanceJobSpecification extends AbstractJobSpecification {

    @Override
    public Long getTimeToLive() {
        return 120 * 1000L; // only stay around for a short while
    }

    @Override
    public boolean isEquivalent(JobSpecification other) {
        return PasswordResetMaintenanceJobSpecification.class.isAssignableFrom(other.getClass());
    }

}
