/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.logging;

import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ObjectId;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AuthenticationHelper;
import org.slf4j.MDC;

import javax.annotation.Resource;
import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Optional;

/**
 * <p>This filter is designed to add material from the state of the request into the logging
 * <a href="http://logback.qos.ch/manual/mdc.html">MDC</a> so that it can be logged on.</p>
 */

public class LoggingFilter implements Filter {

    public final static String KEY_AUTHENTICATEDUSERNICKNAME = "authUserNickname";
    public final static String KEY_USERAGENT = "userAgent";
    public final static String KEY_USERAGENTCODE = "userAgentCode";

    public final static String VALUE_ABSENT = "-";

    public final static String USERAGENT_LEGACY_HAIKUDEPOTUSERAGENT = "X-HDS-Client";

    private enum Agent {
        HAIKUDEPOT("hd"),
        WEBPOSITIVE("wpo"),
        FIREFOX("ffx"),
        CHROME("chr"),
        SAFARI("saf"),
        OPERA("opr"),
        MSIE("msie");

        String code;

        Agent(String code) {
            this.code = code;
        }

        public String getCode() {
            return code;
        }

    }

    @Resource
    ServerRuntime serverRuntime;

    /**
     * <p>This method will try to make a crude guess at the user agent.  This is not very conclusive, but will be
     * enough to help with diagnostics; especially to indicate if it is the desktop C/C++ client or a browser
     * doing something.</p>
     */

    private Optional<Agent> browserDetect(String userAgent) {

        if(!Strings.isNullOrEmpty(userAgent)) {

            if(userAgent.equals(USERAGENT_LEGACY_HAIKUDEPOTUSERAGENT)) {
                return Optional.of(Agent.HAIKUDEPOT);
            }

            if(userAgent.contains("Firefox/")) {
                return Optional.of(Agent.FIREFOX);
            }

            if(userAgent.contains("WebPositive/")) {
                return Optional.of(Agent.WEBPOSITIVE);
            }

            if(userAgent.contains("Trident/")) {
                return Optional.of(Agent.MSIE);
            }

            if(userAgent.contains("Safari/")) {
                if(userAgent.contains("Chrome/")) {
                    if(userAgent.contains("OPR/")) {
                        return Optional.of(Agent.OPERA);
                    }

                    return Optional.of(Agent.CHROME);
                }

                return Optional.of(Agent.SAFARI);
            }

        }

        return Optional.empty();
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain) throws IOException, ServletException {

        try {

            MDC.put(KEY_AUTHENTICATEDUSERNICKNAME, VALUE_ABSENT);
            MDC.put(KEY_USERAGENT, VALUE_ABSENT);
            MDC.put(KEY_USERAGENTCODE, VALUE_ABSENT);

            Optional<ObjectId> authenticatedUserOidOptional = AuthenticationHelper.getAuthenticatedUserObjectId();

            if (authenticatedUserOidOptional.isPresent()) {
                ObjectContext context = serverRuntime.getContext();
                User user = User.getByObjectId(context, authenticatedUserOidOptional.get());
                MDC.put(KEY_AUTHENTICATEDUSERNICKNAME, user.getNickname());
            }

            if(HttpServletRequest.class.isAssignableFrom(request.getClass())) {
                HttpServletRequest hRequest = (HttpServletRequest) request;
                String userAgent = hRequest.getHeader(HttpHeaders.USER_AGENT);

                if(!Strings.isNullOrEmpty(userAgent)) {
                    MDC.put(KEY_USERAGENT, userAgent);

                    Optional<Agent> agentOptional = browserDetect(userAgent);

                    if(agentOptional.isPresent()) {
                        MDC.put(KEY_USERAGENTCODE, agentOptional.get().getCode());
                    }
                }
            }

            chain.doFilter(request,response);
        }
        finally {
            MDC.clear();
        }


    }

    @Override
    public void destroy() {
    }

}
