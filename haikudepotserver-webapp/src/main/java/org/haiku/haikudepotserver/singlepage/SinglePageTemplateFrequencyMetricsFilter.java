/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.singlepage;

import org.springframework.http.HttpMethod;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.regex.Pattern;

/**
 * <p>This filter is able to keep track of the frequency of access for the various templates
 * used by the single-page interface.  It can then use this information to pre-populate the
 * templates into the single-page.</p>
 */

public class SinglePageTemplateFrequencyMetricsFilter implements Filter {

    private static Pattern PATTERN_PATH = Pattern.compile("^/js/app/(controller|directive)/[a-z0-9]+\\.html$");

    @Resource
    private SinglePageTemplateFrequencyMetrics metrics;

    public void setMetrics(SinglePageTemplateFrequencyMetrics metrics) {
        this.metrics = metrics;
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        chain.doFilter(request, response);

        HttpServletResponse servletResponse = (HttpServletResponse) response;

        if(HttpServletResponse.SC_OK == servletResponse.getStatus()) {

            HttpServletRequest servletRequest = (HttpServletRequest) request;

            if(servletRequest.getMethod().equals(HttpMethod.GET.name())) {

                String path = servletRequest.getServletPath();

                if(PATTERN_PATH.matcher(path).matches()) {
                    metrics.increment(path);
                }

            }

        }

    }

    @Override
    public void destroy() {
    }

}
