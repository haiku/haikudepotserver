/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import com.google.common.io.Files;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;

public class FileHelper {

    /**
     * <p>This method will stream the data from the supplied URL into the file.  If it is a suitable
     * URL (http / https) then it is possible to provide a timeout and that will be observed when
     * connecting and retreiving data.</p>
     */

    public static void streamUrlDataToFile(URL url, File file, long timeoutMillis) throws IOException {
        switch(url.getProtocol()) {

            case "http":
            case "https":
                HttpURLConnection connection = null;

                try {
                    connection = (HttpURLConnection) url.openConnection();

                    connection.setConnectTimeout((int) timeoutMillis);
                    connection.setReadTimeout((int) timeoutMillis);
                    connection.setRequestMethod(HttpMethod.GET.name());
                    connection.connect();

                    int responseCode = connection.getResponseCode();

                    if (responseCode == HttpStatus.OK.value()) {
                        try (
                                InputStream inputStream = connection.getInputStream();
                                OutputStream outputStream = new FileOutputStream(file)) {
                            ByteStreams.copy(inputStream, outputStream);
                        }
                    }
                    else {
                        throw new IOException("url request returned " + responseCode);
                    }

                }
                finally {
                    if (null != connection) {
                        connection.disconnect();
                    }
                }
                break;


            case "file":
                Files.copy(new File(url.getFile()), file);
                break;

            default:
                throw new IllegalStateException("the url scheme of " + url.getProtocol() + " is unsupported.");

        }

    }

    /**
     * <p>This method will delete the file specified recursively.</p>
     */

    public static void delete(File f) throws IOException {
        Preconditions.checkArgument(null != f, "the file must be provided");
        if (f.isDirectory()) {
            for (File c : f.listFiles())
                delete(c);
        }
        if (!f.delete())
            throw new FileNotFoundException("Failed to delete file: " + f);
    }

}
