/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.support;

import com.google.common.base.Preconditions;
import com.google.common.io.CountingOutputStream;
import com.google.common.util.concurrent.Uninterruptibles;
import io.avaje.http.api.StreamingOutput;
import jakarta.annotation.Nullable;
import org.haiku.haikudepotserver.graphics.Constants;
import org.haiku.haikudepotserver.graphics.model.Tool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class ToolHelper {

    protected static final Logger LOGGER = LoggerFactory.getLogger(ToolHelper.class);

    /**
     * <p>Runs a tool or pipeline of tools. If a semaphore is supplied then it will run if a
     * permit can be obtained from the {@link Semaphore} in a reasonable timeframe.
     * </p>
     */

    public static StreamingOutput runToolsPipelineWithPermitsAsStreamingOutput(
            @Nullable Semaphore semaphore,
            Tool[] tools,
            InputStream dataFromRequest
    ) throws IOException {
        return (dataForResponse) -> {
            if (null != semaphore) {
                try {
                    if (!semaphore.tryAcquire(Constants.TIMEOUT_ACQUIRE_PERMIT_SECONDS, TimeUnit.SECONDS)) {
                        throw new IOException("unable to acquire a permit to run a tool");
                    }
                    LOGGER.debug("permit acquired");
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new IOException("thread was interrupted obtaining a permit to run a tool");
                }
            } else {
                LOGGER.debug("permit skipped");
            }

            try {
                runToolsPipeline(tools, dataFromRequest, dataForResponse);
            } finally {
                if (null != semaphore) {
                    semaphore.release();
                    LOGGER.debug("permit released");
                }
            }
        };
    }

    public static void runToolsPipeline(
            Tool[] tools,
            InputStream dataFromRequest,
            OutputStream dataForResponse) throws IOException {
        Preconditions.checkArgument(null != dataFromRequest);
        Preconditions.checkArgument(null != dataForResponse);
        Preconditions.checkArgument(null != tools && 0 != tools.length, "tools must be provided");

        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug(
                    "will run % {}",
                    Stream.of(tools)
                            .map(t -> Stream.of(t.args())
                                    .map(s -> String.format("'%s'", s))
                                    .collect(Collectors.joining(" "))
                            )
                            .collect(Collectors.joining(" | "))
            );
        }

        // Create a pipeline of processes based on the tool.

        List<ProcessBuilder> processBuilders = Stream.of(tools).map(t -> new ProcessBuilder(t.args())).toList();
        List<Process> processes = ProcessBuilder.startPipeline(processBuilders);
        List<ToolProcess> toolProcesses = new ArrayList<>(tools.length);
        ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();

        if (processes.size() != tools.length) {
            throw new IllegalStateException("there should be as many tools as there are processes created");
        }

        // Create a thread for each process to extract the std err for each one and gather the tool, the process
        // and the thread into an object to manage.

        for (int i = 0; i < tools.length; i++) {
            final Process process = processes.get(i);
            final Tool tool = tools[i];

            Thread stdErrReadThread = Thread.ofVirtual().start(() -> {
                try (InputStream processErrorStream = process.getErrorStream()) {
                    processErrorStream.transferTo(errorOutputStream);
                } catch (IOException e) {
                    LOGGER.error("unable to read error stream for tool [{}] on thread", tool.args()[0], e);
                }
            });

            toolProcesses.add(new ToolProcess(tools[i], processes.get(i), stdErrReadThread));
        }

        // Stream the stdout from the tail of the pipeline back to the response output stream. Do this in a separate
        // thread.

        Thread stdoutThread = Thread.ofVirtual().start(() -> {
            try (
                    OutputStream processOutputStream = toolProcesses.getFirst().process().getOutputStream();
                    CountingOutputStream countingOutputStream = new CountingOutputStream(processOutputStream)
            ) {
                dataFromRequest.transferTo(countingOutputStream);
                countingOutputStream.flush();
                LOGGER.debug("sent {} bytes to the tool", countingOutputStream.getCount());
            } catch (IOException e) {
                LOGGER.error("unable to write to the tools input stream for pipeline on thread", e);
            }
        });

        try {
            // Synchronously stream in the data to the start of the pipeline.

            try (
                    InputStream processInputStream = toolProcesses.getLast().process().getInputStream();
                    CountingOutputStream countingOutputStream = new CountingOutputStream(dataForResponse)
            ) {
                processInputStream.transferTo(countingOutputStream);
                countingOutputStream.flush();
                LOGGER.debug("received {} bytes from the tool", countingOutputStream.getCount());
            }

            // Wait for the tools to complete processing.

            for (ToolProcess toolProcess : toolProcesses) {
                try {
                    if (!toolProcess.process().waitFor(Duration.ofSeconds(Constants.TIMEOUT_TOOL_EXEC_SECONDS))) {
                        throw new IOException(
                                "tool [%s] failed to complete after %d seconds; %s".formatted(
                                        toLogDescriptor(toolProcess),
                                        Constants.TIMEOUT_TOOL_EXEC_SECONDS,
                                        errorOutputStream.toString(StandardCharsets.UTF_8)
                                ));
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted waiting for [%s] to complete".formatted(toLogDescriptor(toolProcess)));
                }
            }
        } finally {

            // If the tool hung for some reason then terminate it.

            for (ToolProcess toolProcess : toolProcesses) {
                if (toolProcess.process().isAlive()) {
                    toolProcess.process().destroyForcibly();
                    LOGGER.error("did destroy tool [{}] forcibly", toLogDescriptor(toolProcess));
                }
            }

            // Make sure that any resources that were created are destroyed.

            Uninterruptibles.joinUninterruptibly(stdoutThread, Duration.ofSeconds(10));

            for (ToolProcess toolProcess : toolProcesses) {
                Uninterruptibles.joinUninterruptibly(toolProcess.stdErrReadThread(), Duration.ofSeconds(10));
            }

            if (stdoutThread.isAlive()) {
                stdoutThread.interrupt();
            }

            for (ToolProcess toolProcess : toolProcesses) {
                if (toolProcess.stdErrReadThread().isAlive()) {
                    toolProcess.stdErrReadThread().interrupt();
                }
            }
        }

        // If there are exit codes that are non-zero then something has gone wrong; too late because something
        // will have streamed out, but we may as well error and log it.

        for (ToolProcess toolProcess : toolProcesses) {
            if (0 != toolProcess.process().exitValue()) {
                throw new IOException(
                        "tool [%s] returned error code %d; %s".formatted(
                                toLogDescriptor(toolProcess),
                                toolProcess.process().exitValue(),
                                errorOutputStream.toString(StandardCharsets.UTF_8)
                        ));
            }
        }

    }

    private static String toLogDescriptor(ToolProcess toolProcess) {
        return toolProcess.tool.args()[0];
    }

    private record ToolProcess(Tool tool, Process process, Thread stdErrReadThread) {
    }

}
