/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

public class URLHelper {

    protected static Logger LOGGER = LoggerFactory.getLogger(URLHelper.class);

    private static int PAYLOAD_LENGTH_CONNECT_TIMEOUT = 10 * 1000;
    private static int PAYLOAD_LENGTH_READ_TIMEOUT = 10 * 1000;

    public static long payloadLength(URL url) throws IOException {
        Preconditions.checkArgument(null != url, "the url must be supplied");

        long result = -1;
        HttpURLConnection connection = null;

        switch(url.getProtocol()) {

            case "http":
            case "https":

                try {
                    connection = (HttpURLConnection) url.openConnection();

                    connection.setConnectTimeout(PAYLOAD_LENGTH_CONNECT_TIMEOUT);
                    connection.setReadTimeout(PAYLOAD_LENGTH_READ_TIMEOUT);
                    connection.setRequestMethod(HttpMethod.HEAD.name());
                    connection.connect();

                    String contentLengthHeader = connection.getHeaderField(HttpHeaders.CONTENT_LENGTH);

                    if(!Strings.isNullOrEmpty(contentLengthHeader)) {
                        long contentLength;

                        try {
                            contentLength = Long.parseLong(contentLengthHeader);

                            if(contentLength > 0) {
                                result = contentLength;
                            }
                            else {
                                LOGGER.warn("bad content length; {}", contentLength);
                            }
                        }
                        catch(NumberFormatException nfe) {
                            LOGGER.warn("malformed content length; {}", contentLengthHeader);
                        }
                    }
                    else {
                        LOGGER.warn("unable to get the content length header");
                    }

                } finally {
                    if (null != connection) {
                        connection.disconnect();
                    }
                }
                break;

            case "file":
                File file = new File(url.getPath());

                if(file.exists() && file.isFile()) {
                    result = file.length();
                }
                else {
                    LOGGER.warn("unable to find the local file; {}", url.getPath());
                }
                break;

        }

        LOGGER.info("did obtain length forurl; {} - {}", url, result);

        return result;
    }

}
