/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;
import org.haiku.haikudepotserver.job.model.JobSpecification;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class PasswordResetMaintenanceJobSpecification extends AbstractJobSpecification {

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(TimeUnit.SECONDS.toMillis(120)); // only stay around for a short while
    }

}
