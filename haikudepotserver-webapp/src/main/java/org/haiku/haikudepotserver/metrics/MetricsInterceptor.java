/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.metrics;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.metrics.model.RequestStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.annotation.PostConstruct;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Optional;
import java.util.Set;

/**
 * <p>This class intercepts SpringMVC calls and is able to relay information about how long the invocation has
 * taken into the metrics store.  This then feeds into the {@link MetricsFilter}.  This is done in two places
 * like this because injecting into a handler interceptor seems to be difficult.</p>
 */
public class MetricsInterceptor extends HandlerInterceptorAdapter {

    protected static Logger LOGGER = LoggerFactory.getLogger(MetricsInterceptor.class);

    private final Set<String> pathPrefixesForMetricsCapture;

    public MetricsInterceptor(
            Set<String> pathPrefixesForMetricsCapture) {
        this.pathPrefixesForMetricsCapture = Preconditions.checkNotNull(pathPrefixesForMetricsCapture);
    }

    private Optional<String> derivePathPrefix(HttpServletRequest request) {
        final String path = StringUtils.trimLeadingCharacter(request.getRequestURI(), '/');

        return pathPrefixesForMetricsCapture
                .stream()
                .filter(path::startsWith)
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
            Object handler) {
        return true;
    }

    @Override
    public void postHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            ModelAndView modelAndView) {
        int status = response.getStatus();

        if (0 != status && 2 == status / 100) {
            try {
                deriveMetricName(request).ifPresent(
                        mn -> request.setAttribute(MetricsFilter.KEY_REQUEST_METRIC, new RequestStart(mn)));
            } catch (Throwable th) {
                // don't fail the request.
                LOGGER.error("an issue has arisen capturing metrics for a spring-mvc invocation", th);
            }
        }
    }

}
