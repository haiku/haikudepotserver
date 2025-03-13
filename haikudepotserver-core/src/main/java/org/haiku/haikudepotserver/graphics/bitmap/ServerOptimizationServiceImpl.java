/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.bitmap;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * <p>Instance of {@link PngOptimizationService} which makes a network call out to a server
 * to optimize the PNG image. See <code>haikudepotserver-server-graphics</code>.</p>
 */

public class ServerOptimizationServiceImpl implements PngOptimizationService {

    private final static String[] PATH_COMPONENTS = new String[] {"__gfx", "optimize"};

    private final URI uri;
    private final HttpClient httpClient;
    private final ImageHelper imageHelper = new ImageHelper();

    public ServerOptimizationServiceImpl(String baseUri) {
        this.uri = UriComponentsBuilder.fromUriString(baseUri)
                .pathSegment(PATH_COMPONENTS)
                .build()
                .toUri();
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public boolean identityOptimization() {
        return false;
    }

    @Override
    public void optimize(InputStream input, OutputStream output) throws IOException {
        Preconditions.checkArgument(null != input, "expected the input data to be provided");
        Preconditions.checkArgument(null != output, "expected the output data to be provided");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(uri)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString())
                .POST(HttpRequest.BodyPublishers.ofInputStream(() -> input))
                .build();

        try {
            HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

            if (HttpStatusCode.valueOf(response.statusCode()).is2xxSuccessful()) {
                try (InputStream responseStream = response.body()) {
                    responseStream.transferTo(output);
                }
            }

            throw new IOException("the request to the server to produce the image optimization returns ["
                    + response.statusCode() + "]");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("optimization was cancelled", ie);
        }
    }

}
