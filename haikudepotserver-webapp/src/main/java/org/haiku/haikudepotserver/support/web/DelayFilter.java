/*
 * Copyright 2022-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.web;

import com.google.common.util.concurrent.Uninterruptibles;
import org.springframework.beans.factory.annotation.Value;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * <p>This filter is designed to slow down the application server so that
 * it is possible to test situations where the API requests are running
 * slowly back to the client -- a high latency.  This won't be accurate,
 * but will give some simulation.</p>
 */

public class DelayFilter implements Filter {

    @Value("${hds.request.delay-millis:0}")
    private long delayMillis = 0L;

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {
        if (delayMillis > 0L) {
            Uninterruptibles.sleepUninterruptibly(delayMillis, TimeUnit.MILLISECONDS);
        }
        chain.doFilter(request, response);
    }

}
