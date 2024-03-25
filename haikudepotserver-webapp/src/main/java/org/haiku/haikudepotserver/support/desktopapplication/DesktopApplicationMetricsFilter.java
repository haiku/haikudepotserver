/*
 * Copyright 2023-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.desktopapplication;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.haiku.haikudepotserver.metrics.MetricsConstants;
import org.springframework.http.HttpHeaders;

import java.io.IOException;
import java.util.Set;

/**
 * <p>This servlet filter is designed to capture statistics about the
 * clients that are connecting to the application.</p>
 */
public class DesktopApplicationMetricsFilter implements Filter {

    private final MeterRegistry meterRegistry;

    public DesktopApplicationMetricsFilter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest httpServletRequest) {
            DesktopApplicationHelper.tryDeriveVersionFromUserAgent(httpServletRequest.getHeader(HttpHeaders.USER_AGENT))
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

    private void incrementCounterForDesktopApplicationVersion(int[] version) {
        meterRegistry.counter(
                        MetricsConstants.COUNTER_NAME_DESKTOP_REQUESTS,
                        Set.of(Tag.of(MetricsConstants.TAG_NAME_VERSION, DesktopApplicationHelper.versionToString(version))))
                .increment();
    }

}
