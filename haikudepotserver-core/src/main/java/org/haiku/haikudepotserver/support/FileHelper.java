/*
 * Copyright 2015-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.io.Files;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class FileHelper {

    /**
     * <p>This method will stream the data from the supplied URL into the file.  If it is a suitable
     * URL (http / https) then it is possible to provide a timeout and that will be observed when
     * connecting and retrieving data.</p>
     */

    public static void streamUrlDataToFile(URI uri, File file, long timeoutMillis) throws IOException {
        switch (uri.getScheme()) {
            case "http", "https" -> streamHttpUriDataToFile(uri, file, timeoutMillis);
            case "file" -> Files.copy(new File(uri.getPath()), file);
            default -> throw new IllegalStateException("the url scheme of " + uri.getScheme() + " is unsupported.");
        }
    }

    private static void streamHttpUriDataToFile(URI uri, File file, long timeoutMillis) throws IOException {
        try (HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMillis))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build()) {
            HttpResponse<InputStream> response = httpClient
                    .send(HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofMillis(timeoutMillis))
                            .GET()
                            .build(), HttpResponse.BodyHandlers.ofInputStream());

            if (200 == response.statusCode()) {
                try (InputStream inputStream = response.body()) {
                    Files.asByteSink(file).writeFrom(inputStream);
                }
            } else {
                throw new IOException("url request returned http status [" + response.statusCode() + "]");
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted when downloading url to file", ie);
        }
    }

    /**
     * <p>This method will delete the file specified recursively.</p>
     */

    public static void delete(File f) throws IOException {
        Preconditions.checkArgument(null != f, "the file must be provided");
        if (f.isDirectory()) {
            File[] files = f.listFiles();
            if (null != files) {
                for (File c : files)
                    delete(c);
            }
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

}
