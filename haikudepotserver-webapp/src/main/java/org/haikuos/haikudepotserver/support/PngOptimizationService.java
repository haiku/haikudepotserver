/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.UUID;

/**
 * <p>This class provides a service to optimize PNG images.</p>
 */

@Service
public class PngOptimizationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(PngOptimizationService.class);

    @Value("${optipng.path:}")
    private String optiPngPath;

    public boolean isConfigured() {
        return !Strings.isNullOrEmpty(optiPngPath);
    }

    private void quietlyDeleteTemporaryFile(File f) {
        if(f.exists()) {
            if(f.delete()) {
                LOGGER.debug("deleted temporary file; {}", f.getAbsolutePath());
            }
            else {
                LOGGER.warn("failed to delete temporary file; {}", f.getAbsolutePath());
            }
        }
    }

    public byte[] optimize(byte[] input) throws IOException {
        Preconditions.checkArgument(null!=input && 0!=input.length, "the input is not specified");
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        optimize(
                ByteSource.wrap(input),
                new ByteSink() {
                    @Override
                    public OutputStream openStream() throws IOException {
                        return baos;
                    }
                }
        );

        return baos.toByteArray();
    }

    public void optimize(
            ByteSource inputSource,
            ByteSink outputSink) throws IOException {

        Preconditions.checkArgument(null!=inputSource, "the input source must be supplied");
        Preconditions.checkArgument(null!=outputSink, "the output sink must be supplied");
        Preconditions.checkState(isConfigured(), "the service is not suitably configured to optimize png data");

        long startMs = System.currentTimeMillis();

        File temporaryInputFile = File.createTempFile("image-input",".png");
        File temporaryOutputFile = new File(Files.createTempDir(), UUID.randomUUID().toString());

        try {
            inputSource.copyTo(Files.asByteSink(temporaryInputFile));

            Process process = new ProcessBuilder(
                    optiPngPath, "-o5",
                    "-out", temporaryOutputFile.getAbsolutePath(),
                    temporaryInputFile.getAbsolutePath()).start();

            LOGGER.info("did start optipng");

            try (
                    InputStream inputStream = process.getErrorStream();
                    InputStreamReader inputStreamReader = new InputStreamReader(inputStream, Charsets.UTF_8);
                    BufferedReader bufferedReader = new BufferedReader(inputStreamReader)

            ) {

                String line;

                while(null != (line = bufferedReader.readLine())) {
                    LOGGER.debug(line);
                }

            }

            try {
                if (0 != process.waitFor()) {
                    throw new RuntimeException("unable to optimize the png data");
                }
            }
            catch(InterruptedException ie) {
                throw new RuntimeException("interrupted waiting for the optipng process to complete", ie);
            }

            // check the difference between the source and the destination.

            long inputLength = temporaryInputFile.length();
            long outputLength = temporaryOutputFile.length();

            LOGGER.info(
                    "did finish optipng; reduced by {}% ({}ms)",
                    ((inputLength - outputLength) * 100) / inputLength,
                    (System.currentTimeMillis() - startMs));

            Files.asByteSource(temporaryOutputFile).copyTo(outputSink);

        }
        finally {
            quietlyDeleteTemporaryFile(temporaryInputFile);
            quietlyDeleteTemporaryFile(temporaryOutputFile);
        }

    }

}
