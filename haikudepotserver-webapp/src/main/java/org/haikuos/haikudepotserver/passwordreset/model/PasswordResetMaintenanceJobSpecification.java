/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.passwordreset.model;

import org.haikuos.haikudepotserver.job.model.AbstractJobSpecification;
import org.haikuos.haikudepotserver.job.model.JobSpecification;

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
