/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.metrics;

import com.codahale.metrics.MetricRegistry;
import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.metrics.model.RequestStart;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import java.io.IOException;

/**
 * <p>This filter will take any captured metrics about the HTTP request and will
 * store those into the metrics registry.</p>
 */

public class MetricsFilter implements Filter {

    static final String KEY_REQUEST_METRIC = "org.haiku.haikudepotserver.RequestMetric";

    private final MetricRegistry metricRegistry;

    public MetricsFilter(MetricRegistry metricRegistry) {
        this.metricRegistry = Preconditions.checkNotNull(metricRegistry);
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        chain.doFilter(request, response);
        handleMetricCapture(request);
    }

    private void handleMetricCapture(ServletRequest request) {
        Object requestStartObj = request.getAttribute(KEY_REQUEST_METRIC);

        if (null != requestStartObj) {
            RequestStart requestStart = (RequestStart) requestStartObj;
            MetricsHelper.add(metricRegistry, requestStart);
        }
    }

    @Override
    public void destroy() {
    }
}
