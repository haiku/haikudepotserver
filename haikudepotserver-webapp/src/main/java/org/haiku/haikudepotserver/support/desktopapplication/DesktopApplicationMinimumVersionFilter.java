/*
 * Copyright 2018-2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.desktopapplication;

import com.google.common.net.HttpHeaders;
import jakarta.mail.internet.MimeUtility;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.haiku.haikudepotserver.support.IntArrayVersionComparator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Optional;

/**
 * <p>The desktop application develops over time and older versions of the desktop
 * application might no longer be supported.  This filter catches those older
 * versions and disallows them.</p>
 */

public class DesktopApplicationMinimumVersionFilter implements Filter {

    public final static String HEADER_MINIMUM_VERSION = "X-Desktop-Application-Minimum-Version";

    protected static Logger LOGGER = LoggerFactory.getLogger(DesktopApplicationMinimumVersionFilter.class);

    private final int[] minimumVersion;

    private final IntArrayVersionComparator intArrayVersionComparator = new IntArrayVersionComparator();

    public DesktopApplicationMinimumVersionFilter(String minimumVersionString) {
        this.minimumVersion = Optional.ofNullable(minimumVersionString)
                        .map(DesktopApplicationHelper::deriveVersion)
                                .orElse(null);
    }

    /**
     * <p>Returns true if the request contains an acceptable version.</p>
     */

    private boolean checkVersion(String userAgentString) {
        if (null == minimumVersion) {
            return true;
        }

        // don't worry if it is not the desktop application
        if (!DesktopApplicationHelper.matchesUserAgent(userAgentString)) {
            return true;
        }

        return DesktopApplicationHelper.tryDeriveVersionFromUserAgent(userAgentString)
                .filter(version -> intArrayVersionComparator.compare(version, minimumVersion) >= 0)
                .isPresent();
    }

    @Override
    public void init(FilterConfig filterConfig) {
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        String userAgentString = httpRequest.getHeader(HttpHeaders.USER_AGENT);

        if (checkVersion(userAgentString)) {
            chain.doFilter(request, response);
        }
        else {
            String minimumVersionString = DesktopApplicationHelper.versionToString(minimumVersion);

            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            httpServletResponse.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            httpServletResponse.setHeader(HEADER_MINIMUM_VERSION, MimeUtility.encodeText(minimumVersionString));
            httpServletResponse.setContentType(MediaType.TEXT_PLAIN.toString());

            PrintWriter writer = httpServletResponse.getWriter();
            writer.append("The desktop application has a version that is too old to communicate with the\n");
            writer.append("application server.  The minimum version allowed by the application server is\n");
            writer.append("'");
            writer.append(minimumVersionString);
            writer.append("'.  You should upgrade your client.\n");
            writer.close();

            LOGGER.debug("rejected desktop client owing to older version; {}", userAgentString);
        }
    }

    @Override
    public void destroy() {
    }

}
