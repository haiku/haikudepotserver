/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.support;

import com.google.common.io.ByteStreams;
import org.haiku.haikudepotserver.graphics.model.Tool;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.stream.Stream;

public class ToolHelper {

    public static MultiValueMap<String, String> pngHttpHeaders() {
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.IMAGE_PNG_VALUE);
        return headers;
    }

    public static void runTool(
            Tool tool,
            InputStream dataFromRequest,
            OutputStream dataForResponse) throws IOException {
        runToolsPipeline(new Tool[]{tool}, dataFromRequest, dataForResponse);
    }

    public static void runToolsPipeline(
            Tool[] tools,
            InputStream dataFromRequest,
            OutputStream dataForResponse) throws IOException {

        List<ProcessBuilder> processBuilders = Stream.of(tools).map(t -> new ProcessBuilder(t.args())).toList();
        List<Process> processes = ProcessBuilder.startPipeline(processBuilders);

        Thread.ofVirtual().start(() -> {
            try (OutputStream processOutputStream = processes.getFirst().getOutputStream()) {
                dataFromRequest.transferTo(processOutputStream);
            } catch (IOException e) {
                throw new UncheckedIOException("unable to write to the tools input stream", e);
            }
        });

        try (InputStream processInputStream = processes.getLast().getInputStream()) {
            processInputStream.transferTo(dataForResponse);
        }
    }

}
