/*
 * Copyright 2014-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.base.Charsets;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import com.google.common.html.HtmlEscapers;
import com.google.common.net.MediaType;
import org.haiku.haikudepotserver.dataobjects.NaturalLanguage;
import org.haiku.haikudepotserver.api1.support.Constants;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/**
 * <p>This servlet gets hit whenever anything goes wrong in the application from a user perspective.  It will
 * say that a problem has arisen and off the user the opportunity to re-enter the application.  It is as
 * simple as possible to reduce the possibility of the error page failing as well.</p>
 */

public class ErrorServlet extends HttpServlet {

    private final static String PARAM_JSONRPCERRORCODE = "jrpcerrorcd";

    private final static Map<String,String> PREFIX = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Oh darn!",
            NaturalLanguage.CODE_GERMAN, "Herrje!",
            NaturalLanguage.CODE_JAPANESE, "もう!",
            NaturalLanguage.CODE_RUSSIAN, "Вот незадача!",
            NaturalLanguage.CODE_SLOVAK, "Sakra!",
            NaturalLanguage.CODE_SPANISH, "¡Oh rayos!");

    private final static Map<String,String> BODY_GENERAL = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Something has gone wrong with your use of this web application.",
            NaturalLanguage.CODE_GERMAN, "Mit dieser Web-Anwendung ist was schief gegangen.",
            NaturalLanguage.CODE_JAPANESE, "このWebアプリケーションの使い方が何かおかしい。",
            NaturalLanguage.CODE_RUSSIAN, "Что-то пошло не так во время использования вами данного веб-сайта.",
            NaturalLanguage.CODE_SLOVAK, "Niečo sa pokazilo počas vašej práce s touto webovou aplikáciou.",
            NaturalLanguage.CODE_SPANISH, "Algo ha salido mal mientras usaba esta aplicación web.");

    private final static Map<String,String> BODY_NOTFOUND = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "The requested resource was not found.",
            NaturalLanguage.CODE_GERMAN, "Die angefragte Ressource wurde nicht gefunden.",
            NaturalLanguage.CODE_JAPANESE, "要求されたリソースが見つかりません。",
            NaturalLanguage.CODE_RUSSIAN, "Запрашиваемый ресурс не найден.",
            NaturalLanguage.CODE_SLOVAK, "Požadovaný zdroj nebol nájdený.",
            NaturalLanguage.CODE_SPANISH, "El recurso solicitado no se ha encontrado.");

    private final static Map<String,String> BODY_AUTHORIZATIONFAILURE = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Your authentication with the service is expired or you have reached a page that is not accessible with the level of your permissions.",
            NaturalLanguage.CODE_GERMAN, "Die Berechtigungen für diesen Dienst sind abgelaufen, oder der Zugang zur angeforderten Seite erfordert zusätzliche Zugriffsrechte.",
            NaturalLanguage.CODE_JAPANESE, "サービスの認証が期限切れか，またはユーザーの権限では入れないページに触れた。",
            NaturalLanguage.CODE_RUSSIAN, "Действие вашей авторизации в системе истекло, или же вы пытаетесь попасть на страницу, права доступа к которой у вас отсутствуют.",
            NaturalLanguage.CODE_SLOVAK, "Vaša autentifikácia u tejto služby buď vypršala alebo ste narazili na stránku, ktorá nie je prístupná pri vašej úrovni oprávnení.",
            NaturalLanguage.CODE_SPANISH, "La autenticación con el servicio ha expirado o ha llegado a una página que no puede acceder con su nivel de permisos.");

    private final static Map<String,String> ACTION = ImmutableMap.of(
            NaturalLanguage.CODE_ENGLISH, "Start again",
            NaturalLanguage.CODE_GERMAN, "Neu starten",
            NaturalLanguage.CODE_JAPANESE, "再開",
            NaturalLanguage.CODE_RUSSIAN, "Начать сначала",
            NaturalLanguage.CODE_SLOVAK, "Začať znova",
            NaturalLanguage.CODE_SPANISH, "Empezar de nuevo");

    private byte[] pageGeneralBytes = null;

    private Map<String,String> deriveBody(Integer jsonRpcErrorCode, Integer httpStatusCode) {
        if (null != jsonRpcErrorCode && Constants.ERROR_CODE_AUTHORIZATIONFAILURE == jsonRpcErrorCode) {
            return BODY_AUTHORIZATIONFAILURE;
        }

        if (null != httpStatusCode && 404 == httpStatusCode) {
            return BODY_NOTFOUND;
        }

        return BODY_GENERAL;
    }

    private void messageLineAssembly(String naturalLanguageCode, StringBuilder out, Integer jsonRpcErrorCode, Integer httpStatusCode) {
        HtmlEscapers.htmlEscaper();
        out.append("<div class=\"error-message-container\">\n");
        out.append("<div class=\"error-message\">\n");
        out.append("<strong>");
        out.append(HtmlEscapers.htmlEscaper().escape(PREFIX.get(naturalLanguageCode)));
        out.append("</strong>");
        out.append(" ");
        out.append(HtmlEscapers.htmlEscaper().escape(deriveBody(jsonRpcErrorCode, httpStatusCode).get(naturalLanguageCode)));
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

    private void pageAssembly(StringBuilder out, Integer jsonRpcErrorCode, Integer httpStatusCode) {

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

        for (String naturalLanguageCode : new String[] {
                NaturalLanguage.CODE_ENGLISH,
                NaturalLanguage.CODE_GERMAN,
                NaturalLanguage.CODE_JAPANESE,
                NaturalLanguage.CODE_RUSSIAN,
                NaturalLanguage.CODE_SLOVAK
        }) {
            messageLineAssembly(
                    naturalLanguageCode,
                    out,
                    jsonRpcErrorCode,
                    httpStatusCode);
        }

        out.append("</div>\n");
        out.append("</body>\n");
        out.append("</html>\n");
    }

    @Override
    public void init(ServletConfig config) throws ServletException {
        // get together the basic general page as a fallback that exists in memory so that in the worst
        // case scenario, if memory is tight at least we can output this.

        StringBuilder out = new StringBuilder();
        pageAssembly(out, null, null);
        pageGeneralBytes = out.toString().getBytes(Charsets.UTF_8);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {


        try {
            resp.setContentType(MediaType.HTML_UTF_8.toString());
            String jsonRpcErrorCodeString = req.getParameter(PARAM_JSONRPCERRORCODE);
            Integer jsonRpcErrorCode = Strings.isNullOrEmpty(jsonRpcErrorCodeString) ? null : Integer.parseInt(jsonRpcErrorCodeString);

            byte pageBytes[] = pageGeneralBytes;

            // special handling for JSON-RPC errors


            if (null != jsonRpcErrorCode || 404 == resp.getStatus()) {

                try {
                    StringBuilder out = new StringBuilder();
                    pageAssembly(out, jsonRpcErrorCode, resp.getStatus());
                    pageBytes = out.toString().getBytes(Charsets.UTF_8);
                }
                catch(Throwable th) {
                    // swallow
                }

            }

            resp.setContentLength(pageBytes.length);
            resp.getOutputStream().write(pageBytes);
            resp.getOutputStream().flush();

        }
        catch(Throwable th) {
            // swallow
        }

    }
}
