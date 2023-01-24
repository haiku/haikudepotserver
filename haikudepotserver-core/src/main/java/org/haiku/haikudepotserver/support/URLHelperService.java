/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.io.*;
import com.google.common.net.HttpHeaders;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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

@Service
public class URLHelperService {

    protected static Logger LOGGER = LoggerFactory.getLogger(URLHelperService.class);

    private final static int PAYLOAD_LENGTH_CONNECT_TIMEOUT = 10 * 1000;
    private final static int PAYLOAD_LENGTH_READ_TIMEOUT = 10 * 1000;

    private final HttpClient httpClient;

    public URLHelperService() {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(PAYLOAD_LENGTH_CONNECT_TIMEOUT))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

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

    public void transferPayloadToFile(URL url, File targetFile) throws IOException {
        LOGGER.info("will transfer [{}] --> [{}]", url, targetFile);
        String protocol = StringUtils.trimToEmpty(url.getProtocol());

        switch (protocol) {
            case "http", "https" -> {
                FileHelper.streamUrlDataToFile(url, targetFile, PAYLOAD_LENGTH_READ_TIMEOUT);
                LOGGER.info("copied [{}] to [{}]", url, targetFile);
            }
            case "file" -> {
                File sourceFile = new File(url.getPath());
                Files.copy(sourceFile, targetFile);
                LOGGER.info("copied [{}] to [{}]", sourceFile, targetFile);
            }
            default -> LOGGER.warn("unable to transfer for URL scheme [{}]", protocol);
        }
    }

    public Optional<Long> tryGetPayloadLength(URL url) throws IOException {
        Preconditions.checkArgument(null != url, "the url must be supplied");

        Optional<Long> result = Optional.empty();
        String protocol = StringUtils.trimToEmpty(url.getProtocol());

        switch (protocol) {
            case "http", "https" -> {
                try {
                    result = tryGetPayloadLengthHttp(url.toURI());
                } catch (URISyntaxException use) {
                    throw new IllegalStateException("unable to obtain a uri from [" + url + "]");
                }
            }
            case "file" -> {
                File file = new File(url.getPath());
                if (file.exists() && file.isFile()) {
                    result = Optional.of(file.length())
                            .filter(l -> l > 0L);
                } else {
                    LOGGER.warn("unable to find the local file [{}]", url.getPath());
                }
            }
            default -> LOGGER.warn("unable to get the payload length for URL scheme [{}]", protocol);
        }

        result.ifPresent(l -> LOGGER.info("did obtain length [{}b] for url [{}]", l, url));
        return result;
    }

    private Optional<Long> tryGetPayloadLengthHttp(URI uri) throws IOException {
        try {
            HttpResponse<?> response = httpClient.send(HttpRequest.newBuilder(uri)
                    .method("HEAD", HttpRequest.BodyPublishers.noBody())
                            .timeout(Duration.ofMillis(PAYLOAD_LENGTH_READ_TIMEOUT))
                            .build(), HttpResponse.BodyHandlers.discarding());

            switch (response.statusCode()) {
                case 200, 204 -> {
                    Optional<Long> lengthOptional = response.headers().firstValue(HttpHeaders.CONTENT_LENGTH)
                            .map(Long::parseLong);
                    if (lengthOptional.isEmpty()) {
                        LOGGER.warn("missing or bad content-length header at [{}]", uri);
                    }
                    return lengthOptional;
                }
                default -> LOGGER.warn("bad response from [" + uri + "] when getting the length");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            LOGGER.warn("interrupted when downloading url [" + uri + "] to file");
        }

        return Optional.empty();
    }

}
