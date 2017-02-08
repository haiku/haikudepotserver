/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RepositoryDumpExportJobSpecification extends AbstractJobSpecification {

    private final static long TTL_MINUTES = 30;

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(TimeUnit.MINUTES.toMillis(TTL_MINUTES));
    }

}
