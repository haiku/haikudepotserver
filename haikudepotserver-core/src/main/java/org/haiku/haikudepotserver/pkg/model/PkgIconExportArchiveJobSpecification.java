/*
 * Copyright 2015-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

public class PkgIconExportArchiveJobSpecification extends AbstractJobSpecification {

    private final static long TTL_HOURS = 24;

    private final Long ttl = TimeUnit.HOURS.toMillis(TTL_HOURS) + createTimeToLiveJitterMillis(TimeUnit.HOURS);

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(ttl);
    }

}
