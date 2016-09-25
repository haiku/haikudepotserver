/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * <p>This class intercepts SpringMVC calls and is able to relay information about how long the invocation has
 * taken into the metrics store.</p>
 */
public class MetricsInterceptor extends HandlerInterceptorAdapter {

    protected static Logger LOGGER = LoggerFactory.getLogger(MetricsInterceptor.class);

    private final static String ATTRIBUTE_START_TIME = "hds.startTime";

    @Resource
    private MetricRegistry metricRegistry;

    private Set<String> pathPrefixesForMetricsCapture;

    public void setPathPrefixesForMetricsCapture(Collection<String> value) {
        pathPrefixesForMetricsCapture = ImmutableSet.copyOf(value);
    }

    private Optional<String> derivePathPrefix(HttpServletRequest request) {
        final String path = StringUtils.trimLeadingCharacter(request.getRequestURI(), '/');

        return pathPrefixesForMetricsCapture
                .stream()
                .filter((p) -> path.startsWith(p))
                .findFirst();
    }

    private Optional<String> deriveMetricName(HttpServletRequest request) {
        return derivePathPrefix(request).map((p) -> "springmvc-" + p);
    }

    @PostConstruct
    public void init() {
        Preconditions.checkState(null!=pathPrefixesForMetricsCapture, "the path prefixes should have been configured");
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler)
    throws Exception{
        request.setAttribute(ATTRIBUTE_START_TIME, System.currentTimeMillis());
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView)
            throws Exception {
        int status = response.getStatus();

        if (0 != status && 2 == status / 100) {
            try {
                Long startTime = (Long) request.getAttribute(ATTRIBUTE_START_TIME);
                long duration = System.currentTimeMillis() - startTime;
                Optional<String> nameOptional = deriveMetricName(request);

                if (nameOptional.isPresent()) {
                    metricRegistry.counter("counter-" + nameOptional.get()).inc();
                    metricRegistry.timer("timer-" + nameOptional.get()).update(duration, TimeUnit.MILLISECONDS);
                }
            } catch (Throwable th) {
                // don't fail the request.
                LOGGER.error("an issue has arisen capturing metrics for a spring-mvc invocation", th);
            }
        }
    }

}
