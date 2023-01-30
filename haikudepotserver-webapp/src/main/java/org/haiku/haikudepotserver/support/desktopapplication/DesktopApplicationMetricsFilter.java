/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.desktopapplication;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import org.haiku.haikudepotserver.metrics.MetricsConstants;
import org.springframework.http.HttpHeaders;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;
import java.util.Set;

/**
 * <p>This servlet filter is designed to capture statistics about the
 * clients that are connecting to the application.</p>
 */
public class DesktopApplicationMetricsFilter implements Filter {

    private final MeterRegistry meterRegistry;

    private static final String USER_AGENT_PREFIX_HAIKU_DEPOT = "HaikuDepot/";

    public DesktopApplicationMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest httpServletRequest) {
            Optional.ofNullable(httpServletRequest.getHeader(HttpHeaders.USER_AGENT))
                    .filter(h -> h.startsWith(USER_AGENT_PREFIX_HAIKU_DEPOT))
                    .map(h -> h.substring(USER_AGENT_PREFIX_HAIKU_DEPOT.length()))
                    .ifPresent(this::incrementCounterForDesktopApplicationVersion);
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        Filter.super.init(filterConfig);
    }

    @Override
    public void destroy() {
        Filter.super.destroy();
    }

    private void incrementCounterForDesktopApplicationVersion(String version) {
        meterRegistry.counter(
                        MetricsConstants.COUNTER_NAME_DESKTOP_REQUESTS,
                        Set.of(Tag.of(MetricsConstants.TAG_NAME_VERSION, version)))
                .increment();
    }

}
