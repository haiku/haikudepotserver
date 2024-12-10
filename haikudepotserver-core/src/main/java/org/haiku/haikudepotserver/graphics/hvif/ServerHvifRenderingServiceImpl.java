/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.hvif;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class ServerHvifRenderingServiceImpl implements HvifRenderingService {

    private final static String[] PATH_COMPONENTS = new String[] {"__gfx", "hvif2png"};

    private final static String KEY_SIZE = "sz";

    private final URI uri;
    private final HttpClient httpClient;

    public ServerHvifRenderingServiceImpl(String baseUri) {
        this.uri = UriComponentsBuilder.fromUriString(baseUri)
                .pathSegment(PATH_COMPONENTS)
                .build()
                .toUri();
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public byte[] render(int size, byte[] input) throws IOException {
        URI renderUri = UriComponentsBuilder.fromUri(uri)
                .queryParam(KEY_SIZE, Integer.toString(size))
                .build().toUri();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(renderUri)
                .header(HttpHeaders.CONTENT_TYPE, MediaType.PNG.toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(input))
                .build();

        try {
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());

            if (HttpStatusCode.valueOf(response.statusCode()).is2xxSuccessful()) {
                return response.body();
            }

            throw new IOException("the request to the server to produce the hvif returns ["
                    + response.statusCode() + "]");
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("hvif rendering was cancelled", ie);
        }
    }

}
