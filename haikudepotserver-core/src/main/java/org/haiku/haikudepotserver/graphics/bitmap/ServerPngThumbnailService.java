/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.bitmap;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

/**
 * <p>An implementation of {@link PngThumbnailService} which will call out to a server to
 * produce the thumbnail. See <code>haikudepotserver-server-graphics</code>.</p>
 */

public class ServerPngThumbnailService implements PngThumbnailService{

    private final static String[] PATH_COMPONENTS = new String[] {"__gfx", "thumbnail"};

    private final static String KEY_HEIGHT = "h";
    private final static String KEY_WIDTH = "w";

    private final URI uri;
    private final HttpClient httpClient;
    private final ImageHelper imageHelper = new ImageHelper();

    public ServerPngThumbnailService(String baseUri) {
        this.uri = UriComponentsBuilder.fromUriString(baseUri)
                .pathSegment(PATH_COMPONENTS)
                .build()
                .toUri();
        httpClient = HttpClient.newHttpClient();
    }

    @Override
    public byte[] thumbnail(byte[] input, int width, int height) throws IOException {
        Preconditions.checkArgument(null != input);
        Preconditions.checkArgument(width > 0, "width  must be greater than 0");
        Preconditions.checkArgument(height > 0, "height must be greater than 0");

        // checks to make sure that the image is actually a PNG.

        ImageHelper.Size size = imageHelper.derivePngSize(input);

        if (null == size) {
            throw new IOException("unable to derive size for png image");
        }

        // check to see if the screenshot needs to be resized to fit.
        if (size.width > width || size.height > height) {
            URI renderUri = UriComponentsBuilder.fromUri(uri)
                    .queryParam(KEY_HEIGHT, Integer.toString(height))
                    .queryParam(KEY_WIDTH, Integer.toString(width))
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

                throw new IOException("the request to the server to produce the thumbnail returns ["
                        + response.statusCode() + "]");
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new IOException("optimization was cancelled", ie);
            }
        }

        return input;
    }

}
