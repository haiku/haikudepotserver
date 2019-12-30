/*
 * Copyright 2018-2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

public class URLHelper {

    protected static Logger LOGGER = LoggerFactory.getLogger(URLHelper.class);

    private final static int PAYLOAD_LENGTH_CONNECT_TIMEOUT = 10 * 1000;
    private final static int PAYLOAD_LENGTH_READ_TIMEOUT = 10 * 1000;

    public static boolean isValidInfo(String urlString) {

        if (StringUtils.isNotBlank(urlString)) {
            try {
                new URI(urlString);
                return true;
            } catch (URISyntaxException ignore) {
            }
        }

        return false;
    }

    public static long payloadLength(URL url) throws IOException {
        Preconditions.checkArgument(null != url, "the url must be supplied");

        long result = -1;

        switch(url.getProtocol()) {

            case "http":
            case "https":
                try {
                    result = payloadLengthHttp(url.toURI());
                } catch (URISyntaxException use) {
                    throw new IllegalStateException("unable to obtain uri from [" + url + "]");
                }
                break;

            case "file":
                File file = new File(url.getPath());

                if (file.exists() && file.isFile()) {
                    result = file.length();
                } else {
                    LOGGER.warn("unable to find the local file; {}", url.getPath());
                }
                break;

        }

        LOGGER.info("did obtain length [{}b] for url [{}]", result, url);

        return result;
    }

    private static long payloadLengthHttp(URI uri) throws IOException {
        try {
            HttpResponse<byte[]> response = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofMillis(PAYLOAD_LENGTH_CONNECT_TIMEOUT))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build()
                    .send(HttpRequest.newBuilder(uri)
                            .GET()
                            .timeout(Duration.ofMillis(PAYLOAD_LENGTH_READ_TIMEOUT))
                            .build(), HttpResponse.BodyHandlers.ofByteArray());

            if (200 == response.statusCode()) {
                Optional<Long> lengthOptional = response.headers().firstValue(HttpHeaders.CONTENT_LENGTH)
                        .map(Long::parseLong);

                if (lengthOptional.isEmpty()) {
                    LOGGER.warn("missing or bad content-length header at [{}]", uri);
                }

                return lengthOptional.get();
            } else {
                LOGGER.warn("bad response from [" + uri + "] when getting the length");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("interrupted when downloading url [" + uri + "] to file");
        }

        return -1;
    }

}
