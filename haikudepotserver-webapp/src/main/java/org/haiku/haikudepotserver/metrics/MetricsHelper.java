/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.metrics;

import com.codahale.metrics.MetricRegistry;
import org.haiku.haikudepotserver.metrics.model.RequestStart;

import java.time.Instant;
import java.util.concurrent.TimeUnit;

public class MetricsHelper {

    static void add(MetricRegistry metricRegistry, String name, long durationSeconds) {
        metricRegistry.counter(name + ".counter").inc();
        metricRegistry.timer(name + ".timer").update(durationSeconds, TimeUnit.SECONDS);
    }

    static void add(MetricRegistry metricRegistry, RequestStart requestStart) {
        add(metricRegistry, requestStart.getName(),
                Instant.now().getEpochSecond() - requestStart.getStart().getEpochSecond());
    }

}
