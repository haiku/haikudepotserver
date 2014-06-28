/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.web;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.MediaType;
import org.haikuos.haikudepotserver.api1.support.Constants;
import org.haikuos.haikudepotserver.dataobjects.NaturalLanguage;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * <p>This filter gets hit whenever anything goes wrong in the application from a user perspective.  It will
 * say that a problem has arisen and off the user the opportunity to re-enter the application.  It is as
 * simple as possible to reduce the possibility of the error page failing as well.</p>
 */

public class ErrorFilter implements Filter {

    private final static String PARAM_JSONRPCERRORCODE = "jrpcerrorcd";

    private final static Map<String,String> PREFIX = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Oh darn!",
            NaturalLanguage.CODE_GERMAN, "Oh mei!");

    private final static Map<String,String> BODY_GENERAL = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Something has gone wrong with your use of this web application.",
            NaturalLanguage.CODE_GERMAN, "Etwas ist falsch gegangen mit Ihre Benutzung des Anwendungs.");

    private final static Map<String,String> BODY_AUTHORIZATIONFAILURE = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Your authentication with the service is expired or you have reached a page that is not accessible with the level of your permissions.",
            NaturalLanguage.CODE_GERMAN, "Die Berechtigungen für diesen Dienst sind abgelaufen, oder der Zugang zur angeforderten Seite erfordert zusätzliche Zugriffsrechte.");

    private final static Map<String,String> ACTION = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Start again",
            NaturalLanguage.CODE_GERMAN, "Neue anfangen");

    private byte[] pageGeneralBytes = null;

    private Map<String,String> deriveBody(Integer jsonRpcErrorCode) {
        if (null != jsonRpcErrorCode && Constants.ERROR_CODE_AUTHORIZATIONFAILURE == jsonRpcErrorCode) {
            return BODY_AUTHORIZATIONFAILURE;
        }
        return BODY_GENERAL;
    }

    private void messageLineAssembly(String naturalLanguageCode, StringBuilder out, Integer jsonRpcErrorCode) {
        HtmlEscapers.htmlEscaper();
        out.append("<div class=\"error-message-container\">\n");
        out.append("<div class=\"error-message\">\n");
        out.append("<strong>");
        out.append(HtmlEscapers.htmlEscaper().escape(PREFIX.get(naturalLanguageCode)));
        out.append("</strong>");
        out.append(" ");
        out.append(HtmlEscapers.htmlEscaper().escape(deriveBody(jsonRpcErrorCode).get(naturalLanguageCode)));
        out.append("</div>\n");
        out.append("<div class=\"error-startagain\">");
        out.append(" &#8594; ");
        out.append("<a href=\"/\">");
        out.append(HtmlEscapers.htmlEscaper().escape(ACTION.get(naturalLanguageCode)));
        out.append("</a>");
        out.append("</div>\n");
        out.append("</div>\n");
    }

    /**
     * <p>Assemble the page using code in order to reduce the chance of things going wrong loading resources and so
     * on.</p>
     */

    private void pageAssembly(StringBuilder out, Integer jsonRpcErrorCode) {

        out.append("<html>\n");
        out.append("<head>\n");
        out.append("<title>HaikuDepotServer - Error</title>\n");
        out.append("<style>\n");
        out.append("body { background-color: #336698; position: relative; font-family: sans-serif; }\n");
        out.append("h1 { text-align: center; }\n");
        out.append("#error-container { color: white; width: 420px; height: 320px; margin:0 auto; margin-top: 72px; }\n");
        out.append("#error-container .error-message-container { margin-bottom: 20px; }\n");
        out.append("#error-container .error-startagain { text-align: right; }\n");
        out.append("#error-container .error-startagain > a { color: white; }\n");
        out.append("#error-image { text-align: center; }\n");
        out.append("</style>\n");
        out.append("</head>\n");
        out.append("<body>\n");
        out.append("<div id=\"error-container\">\n");
        out.append("<div id=\"error-image\"><img src=\"/img/haikudepot-error.svg\"></div>\n");
        out.append("<h1>Haiku Depot Server</h1>\n");

        for(String naturalLanguageCode : new String[] {
                NaturalLanguage.CODE_ENGLISH,
                NaturalLanguage.CODE_GERMAN }) {
            messageLineAssembly(naturalLanguageCode, out, jsonRpcErrorCode);
        }

        out.append("</div>\n");
        out.append("</body>\n");
        out.append("</html>\n");
    }

    @Override
    public void init(FilterConfig filterConfig) throws ServletException {

        // get together the basic general page as a fallback that exists in memory so that in the worst
        // case scenario, if memory is tight at least we can output this.

        StringBuilder out = new StringBuilder();
        pageAssembly(out, null);
        pageGeneralBytes = out.toString().getBytes(Charsets.UTF_8);
    }

    @Override
    public void doFilter(
            ServletRequest request,
            ServletResponse response,
            FilterChain chain) throws IOException, ServletException {

        try {
            HttpServletRequest httpRequest = (HttpServletRequest) request;
            HttpServletResponse httpResponse = (HttpServletResponse) response;
            httpResponse.setContentType(MediaType.HTML_UTF_8.toString());
            String jsonRpcErrorCodeString = httpRequest.getParameter(PARAM_JSONRPCERRORCODE);

            byte pageBytes[] = pageGeneralBytes;

            if(!Strings.isNullOrEmpty(jsonRpcErrorCodeString)) {

                try {
                    Integer jsonRpcErrorCode = Integer.parseInt(jsonRpcErrorCodeString);
                    StringBuilder out = new StringBuilder();
                    pageAssembly(out, jsonRpcErrorCode);
                    pageBytes = out.toString().getBytes(Charsets.UTF_8);
                }
                catch(Throwable th) {
                    pageBytes = pageGeneralBytes;
                }

            }

            httpResponse.setContentLength(pageBytes.length);
            httpResponse.getOutputStream().write(pageBytes);
            httpResponse.getOutputStream().flush();

        }
        catch(Throwable th) {
            // eat it.
        }

    }

    @Override
    public void destroy() {
        pageGeneralBytes = null;
    }

}
