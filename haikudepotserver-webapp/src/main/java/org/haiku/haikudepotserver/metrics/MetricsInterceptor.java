/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.metrics;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.metrics.model.RequestStart;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * <p>This class intercepts SpringMVC calls and is able to relay information about how long the invocation has
 * taken into the metrics store.  This then feeds into the {@link MetricsFilter}.  This is done in two places
 * like this because injecting into a handler interceptor seems to be difficult.</p>
 */
public class MetricsInterceptor extends HandlerInterceptorAdapter {

    protected static Logger LOGGER = LoggerFactory.getLogger(MetricsInterceptor.class);

    public final String label;

    public MetricsInterceptor(String label) {
        this.label = "springmvc." + Preconditions.checkNotNull(label);
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
        HttpStatus status = HttpStatus.valueOf(response.getStatus());

        if (status.is2xxSuccessful() || status.is3xxRedirection()) {
            try {
                request.setAttribute(MetricsFilter.KEY_REQUEST_METRIC, new RequestStart(label));
            } catch (Throwable th) {
                // don't fail the request.
                LOGGER.error("an issue has arisen capturing metrics for a spring-mvc invocation", th);
            }
        }
    }

}
