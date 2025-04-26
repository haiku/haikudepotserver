/*
 * Copyright 2014-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.base.Strings;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.MediaType;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.haiku.haikudepotserver.api1.support.Constants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.web.context.support.WebApplicationContextUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;

/**
 * <p>This servlet gets hit whenever anything goes wrong in the application from a user perspective.  It will
 * say that a problem has arisen and off the user the opportunity to re-enter the application.</p>
 */

public class ErrorServlet extends HttpServlet {

    protected final static Logger LOGGER = LoggerFactory.getLogger(ErrorServlet.class);

    private final static String PARAM_JSONRPCERRORCODE = "jrpcerrorcd";

    private byte[] fallbackPageGeneralBytes = null;

    private MessageSource messageSource;

    @Override
    public void init(ServletConfig config) throws ServletException {

        ApplicationContext context = WebApplicationContextUtils.getRequiredWebApplicationContext(
                config.getServletContext());
        messageSource = context.getBean(MessageSource.class);

        // get together the basic general page as a fallback that exists in memory so that in the worst
        // case scenario, if memory is tight at least we can output this.

        try {
            fallbackPageGeneralBytes = renderPageToByteArray(null, null, null);
        }
        catch (IOException ioe) {
            throw new ServletException("unable to initialize the fallback page", ioe);
        }
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) {

        try {
            attemptLogError(req);
        }
        catch(Throwable th) {
            // swallow
        }

        try {
            String jsonRpcErrorCodeString = req.getParameter(PARAM_JSONRPCERRORCODE);
            Integer jsonRpcErrorCode = Strings.isNullOrEmpty(jsonRpcErrorCodeString) ? null : Integer.parseInt(jsonRpcErrorCodeString);
            byte[] pageBytes = renderPageToByteArrayOrFallback(req.getLocale(), jsonRpcErrorCode, resp.getStatus());
            resp.setContentType(MediaType.HTML_UTF_8.toString());
            resp.setContentLength(pageBytes.length);
            resp.getOutputStream().write(pageBytes);
            resp.getOutputStream().flush();
        }
        catch(Throwable th) {
            // swallow
        }

    }

    private String deriveBodyKey(Integer jsonRpcErrorCode, Integer httpStatusCode) {
        if (null != jsonRpcErrorCode && Constants.ERROR_CODE_AUTHORIZATIONFAILURE == jsonRpcErrorCode) {
            return "error.authorizationfailure.description";
        }

        if (null != httpStatusCode && 404 == httpStatusCode) {
            return "error.notfound.description";
        }

        return "error.general.description";
    }

    private void renderTextualMessages(
            Writer out, Locale locale,
            Integer jsonRpcErrorCode, Integer httpStatusCode) throws IOException {
        String title = messageSource.getMessage("error.title", null, locale);
        String body = messageSource.getMessage(deriveBodyKey(jsonRpcErrorCode, httpStatusCode), null, locale);
        String actionTitle = messageSource.getMessage("error.action.title", null, locale);

        out.append("<div class=\"error-message-container\">\n");
        out.append("<div class=\"error-message\">\n");
        out.append("<strong>");
        out.append(HtmlEscapers.htmlEscaper().escape(title));
        out.append("</strong>");
        out.append(" ");
        out.append(HtmlEscapers.htmlEscaper().escape(body));
        out.append("</div>\n");
        out.append("<div class=\"error-startagain\">");
        out.append(" &#8594; ");
        out.append("<a href=\"/\">");
        out.append(HtmlEscapers.htmlEscaper().escape(actionTitle));
        out.append("</a>");
        out.append("</div>\n");
        out.append("</div>\n");
    }

    private byte[] renderPageToByteArrayOrFallback(
            Locale locale,
            Integer jsonRpcErrorCode, Integer httpStatusCode) {

        byte[] result = fallbackPageGeneralBytes;

        try {
            result = renderPageToByteArray(locale, jsonRpcErrorCode, httpStatusCode);
        } catch (Throwable ignore) {
            // ignore
        }

        return result;
    }

    private byte[] renderPageToByteArray(
            Locale locale,
            Integer jsonRpcErrorCode, Integer httpStatusCode) throws IOException {
        try (
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                OutputStreamWriter writer = new OutputStreamWriter(outputStream, StandardCharsets.UTF_8) )
        {
            renderPage(writer, locale, jsonRpcErrorCode, httpStatusCode);
            writer.flush();
            outputStream.flush();
            return outputStream.toByteArray();
        }
    }

    /**
     * <p>Assemble the page using code in order to reduce the chance of things going wrong loading resources and so
     * on.</p>
     */

    private void renderPage(
            Writer out, Locale locale,
            Integer jsonRpcErrorCode, Integer httpStatusCode) throws IOException {

        if (null == locale) {
            locale = Locale.ENGLISH;
        }

        out.append("<html>\n");
        out.append("<head>\n");
        out.append("<link rel=\"icon\" type=\"image/png\" href=\"/__img/haikudepot16.png\" sizes=\"16x16\">\n");
        out.append("<link rel=\"icon\" type=\"image/png\" href=\"/__img/haikudepot32.png\" sizes=\"32x32\">\n");
        out.append("<link rel=\"icon\" type=\"image/png\" href=\"/__img/haikudepot64.png\" sizes=\"64x64\">\n");
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
        out.append("<div id=\"error-image\"><img src=\"/__img/haikudepot-error.svg\"></div>\n");
        out.append("<h1>Haiku Depot Server</h1>\n");

        renderTextualMessages(
                out,
                locale,
                jsonRpcErrorCode,
                httpStatusCode);

        out.append("</div>\n");
        out.append("</body>\n");
        out.append("</html>\n");
    }

    private void attemptLogError(HttpServletRequest req) {
        Number statusCode = Optional.ofNullable((Number) req.getAttribute(RequestDispatcher.ERROR_STATUS_CODE)).orElse(0);
        String requestUri = (String) req.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        switch (statusCode.intValue()) {
            case 404:
                LOGGER.info("not found [{}]", requestUri);
                break;
            default:
                Throwable throwable = (Throwable) req.getAttribute(RequestDispatcher.ERROR_EXCEPTION);
                String message = (String) req.getAttribute(RequestDispatcher.ERROR_MESSAGE);
                LOGGER.error("failed request to [{}] ({}); {}", requestUri, statusCode, message, throwable);
                break;
        }
    }

}
