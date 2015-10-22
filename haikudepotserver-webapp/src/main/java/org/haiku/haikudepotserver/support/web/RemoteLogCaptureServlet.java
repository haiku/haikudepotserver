/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;

/**
 * <p>This servlet is just present to capture log data from the remote application which is the JavaScript
 * single-page application.</p>
 */

public class RemoteLogCaptureServlet extends HttpServlet {

    private static final int MAX_LEN = 2048;
    private static final String NAME_LOGGER = "org.haiku.haikudepotserver.js";

    protected static Logger LOGGER_CAPTURE = LoggerFactory.getLogger(NAME_LOGGER);

    /**
     * <p>Reads only the specified limit of characters from the reader and discards the rest.</p>
     */

    private String extractString(Reader reader, int limit) throws IOException {
        StringWriter writer = new StringWriter();
        char buffer[] = new char[256];
        int len;

        while(-1 != (len = reader.read(buffer))) {
            int lenRemaining = limit - writer.getBuffer().length();

            if(lenRemaining > 0) {
                writer.write(buffer,0,Math.min(len, lenRemaining));
            }
        }

        return writer.getBuffer().toString();
    }

    /**
     * <p>Grabs the log line from the request and returns it.  It may take the opportunity to capture something
     * else from the request?</p>
     */

    private String extractString(HttpServletRequest request, int limit) throws IOException {
        return extractString(request.getReader(), limit);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {

        // we read here in a relatively simplistic way in order to avoid reading too much material
        // into the string should the client go out of control send send too much material
        // through.

        String payload = extractString(req, MAX_LEN);
        LOGGER_CAPTURE.error(payload);
    }
}
