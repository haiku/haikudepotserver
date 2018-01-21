/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.metrics;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.googlecode.jsonrpc4j.InvocationListener;
import org.haiku.haikudepotserver.metrics.model.RequestStart;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This invocation listener will capture metrics about the requests coming in and will
 * put those into the {@link com.codahale.metrics.MetricRegistry}.</p>
 */
public class MetricsInvocationListener implements InvocationListener {

    private static Pattern PATTERN_API_PACKAGE = Pattern.compile("^.+\\.api([0-9]+)$");

    private final MetricRegistry metricRegistry;

    public MetricsInvocationListener(MetricRegistry metricRegistry) {
        this.metricRegistry = Preconditions.checkNotNull(metricRegistry);
    }

    private String formulateMetricName(Class klass) {
        Matcher matcher = PATTERN_API_PACKAGE.matcher(klass.getPackage().getName());
        return "jrpc-api" + (matcher.matches() ? matcher.group(1) : "") + "." + klass.getSimpleName();
    }

    @Override
    public void willInvoke(Method method, List<JsonNode> arguments) {
    }

    @Override
    public void didInvoke(
            Method method,
            List<JsonNode> arguments,
            Object result,
            Throwable t,
            long duration) {
        Preconditions.checkState(null!=metricRegistry, "the metrics registry must be configured");
        String name = formulateMetricName(method.getDeclaringClass()) + "#" + method.getName();
        MetricsHelper.add(metricRegistry, name, TimeUnit.SECONDS.convert(duration, TimeUnit.MILLISECONDS));
    }
}
