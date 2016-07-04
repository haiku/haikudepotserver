package org.haiku.haikudepotserver.support.jsonrpc4j;

import com.codahale.metrics.MetricRegistry;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.googlecode.jsonrpc4j.InvocationListener;

import javax.annotation.PostConstruct;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>This invocation listener will capture metrics about the requests coming in and will
 * put those into the {@link com.codahale.metrics.MetricRegistry}.</p>
 */
public class MetricsInvocationListener implements InvocationListener {

    private static Pattern PATTERN_API_PACKAGE = Pattern.compile("^.+\\.api([0-9]+)$");

    private MetricRegistry metricRegistry;

    LoadingCache<Class, String> apiClassToCounterName = CacheBuilder.newBuilder()
            .maximumSize(1000)
            .expireAfterWrite(60, TimeUnit.MINUTES)
            .build(
                    new CacheLoader<Class, String>() {
                        public String load(Class declaringClass) {
                            Matcher matcher = PATTERN_API_PACKAGE.matcher(declaringClass.getPackage().getName());
                            return "api" + (matcher.matches() ? matcher.group(1) : "") + "." + declaringClass.getSimpleName();
                        }
                    });

    public void setMetricRegistry(MetricRegistry metricRegistry) {
        this.metricRegistry = metricRegistry;
    }

    @PostConstruct
    public void init() {
        Preconditions.checkState(null!=metricRegistry, "the metric registry must be provided");
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
        String name = apiClassToCounterName.getUnchecked(method.getDeclaringClass()) + "#" + method.getName();
        metricRegistry.counter(name).inc();
    }
}
