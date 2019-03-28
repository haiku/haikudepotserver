/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.desktopapplication;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.common.net.HttpHeaders;
import org.haiku.haikudepotserver.support.IntArrayVersionComparator;
import org.haiku.haikudepotserver.support.logging.LoggingFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <p>The desktop application develops over time and older versions of the desktop
 * application might no longer be supported.  This filter catches those older
 * versions and disallows them.</p>
 */

@Component
public class DesktopApplicationMinimumVersionFilter implements Filter {

    private final static Pattern PATTERN_MINIMUMVERSIONSTRING = Pattern.compile("^[0-9]+(\\.[0-9]+)*$");
    private final static Pattern PATTERN_DESKTOPAPPLICATIONUSERAGENT = Pattern.compile("^HaikuDepot/([0-9]+(\\.[0-9]+)*)");
    public final static String HEADER_MINIMUM_VERSION = "X-Desktop-Application-Minimum-Version";

    protected static Logger LOGGER = LoggerFactory.getLogger(DesktopApplicationMinimumVersionFilter.class);

    private final String minimumVersionString;

    private final int[] minimumVersion;

    private final IntArrayVersionComparator intArrayVersionComparator = new IntArrayVersionComparator();

    public DesktopApplicationMinimumVersionFilter(
            @Value("${desktop.application.version.min:}") String minimumVersionString) {
        this.minimumVersionString = minimumVersionString;
        this.minimumVersion = deriveVersion(minimumVersionString);
    }

    private int[] deriveVersion(String versionString) {
        if (!Strings.isNullOrEmpty(versionString)) {
            int[] version = parseVersion(versionString);

            if (null == version) {
                throw new IllegalStateException("not able to parse the minimum version string; " + versionString);
            }

            LOGGER.info("desktop application min version; {}", versionString);
            return version;
        }

        return null;
    }

    private int[] parseVersion(String versionString) {
        if (PATTERN_MINIMUMVERSIONSTRING.matcher(versionString).matches()) {
            List<String> items = Lists.newArrayList(Splitter.on('.').split(versionString));
            int[] result = new int[items.size()];

            for (int i = 0; i < items.size(); i++) {
                result[i] = Integer.parseInt(items.get(i));
            }

            return result;
        }

        return null;
    }

    private int[] parseVersionFromUserAgentString(String userAgentString) {

        if (!Strings.isNullOrEmpty(userAgentString)) {

            // hopefully this can be dropped soon.
             if (userAgentString.equals(LoggingFilter.USERAGENT_LEGACY_HAIKUDEPOTUSERAGENT)) {
                 return new int[] { 0, 0, 0};
             }

            Matcher desktopApplicationUserAgentMatcher = PATTERN_DESKTOPAPPLICATIONUSERAGENT.matcher(userAgentString);

            if (desktopApplicationUserAgentMatcher.matches()) {
                return parseVersion(desktopApplicationUserAgentMatcher.group(1));
            }
        }

        return null;
    }

    /**
     * <p>Returns true if the request contains an acceptable version.</p>
     */

    private boolean checkVersion(String userAgentString) {

        if (null == minimumVersion) {
            return true;
        }

        int[] requestVersion = parseVersionFromUserAgentString(userAgentString);

        return
                null == requestVersion
                || intArrayVersionComparator.compare(requestVersion, minimumVersion) >= 0;
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
            HttpServletResponse httpServletResponse = (HttpServletResponse) response;
            httpServletResponse.setStatus(HttpServletResponse.SC_PRECONDITION_FAILED);
            httpServletResponse.setHeader(HEADER_MINIMUM_VERSION, minimumVersionString);
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
