/*
 * Copyright 2017-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.repository.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class RepositoryDumpExportJobSpecification extends AbstractJobSpecification {

    /**
     * <p>The reference data is able to be left for a long time as it rarely changes.</p>
     */
    private final static long TT_HOURS = 24;

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(TimeUnit.HOURS.toMillis(TT_HOURS) + createTimeToLiveJitterMillis(TimeUnit.HOURS));
    }

}
