/*
 * Copyright 2023-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.metrics.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;

import java.time.Duration;
import java.util.Optional;

public class MetricsGeneralReportJobSpecification extends AbstractJobSpecification {

    @Override
    public Optional<Long> tryGetTimeToLiveMillis() {
        return Optional.of(Duration.ofMinutes(10).toMillis());
    }

}
