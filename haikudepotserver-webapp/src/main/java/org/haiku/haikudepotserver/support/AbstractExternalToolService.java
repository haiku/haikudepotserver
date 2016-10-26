/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import com.google.common.base.Charsets;
import com.google.common.base.Preconditions;
import com.google.common.io.ByteSink;
import com.google.common.io.ByteSource;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>An abstract superclass for services that are able to call out to another piece of software to
 * perform some task such as render an HVIF or optimize a PNG.  This only really deals with very
 * simple situations.</p>
 */

public abstract class AbstractExternalToolService<T> {

    protected static Logger LOGGER = LoggerFactory.getLogger(AbstractExternalToolService.class);

    private static long TIMEOUT = 30 * 1000;

    private File temporaryDirectory = null;

    private synchronized File getTemporaryDirectory() {
        if(null==temporaryDirectory) {
            temporaryDirectory = Files.createTempDir();
            temporaryDirectory.deleteOnExit();
            LOGGER.info("did create the temporary directory; {}", temporaryDirectory);
        }

        return temporaryDirectory;
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

    public abstract List<String> createArguments(T context, File temporaryInputFile, File temporaryOutputFile);

    protected byte[] execute(T context, byte[] input) throws IOException {
        Preconditions.checkArgument(null != input && 0 != input.length, "the input is not specified");
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();

        execute(
                context,
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

    private void execute(
            T context,
            ByteSource inputSource,
            ByteSink outputSink) throws IOException {

        Preconditions.checkArgument(null != inputSource, "the input source must be supplied");
        Preconditions.checkArgument(null!=outputSink, "the output sink must be supplied");

        long startMs = System.currentTimeMillis();

        File temporaryInputFile = File.createTempFile("image-input",".png");
        File temporaryOutputFile = new File(getTemporaryDirectory(), UUID.randomUUID().toString());

        try {
            inputSource.copyTo(Files.asByteSink(temporaryInputFile));

            List<String> args = createArguments(context, temporaryInputFile, temporaryOutputFile);
            Process process = new ProcessBuilder(args).start();

            LOGGER.debug("did start " + args.get(0));

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
                if (process.waitFor(TIMEOUT, TimeUnit.MILLISECONDS)) {
                    if(0 != process.exitValue()) {
                        throw new RuntimeException("unable to execute " + args.get(0));
                    }
                }
                else {
                    process.destroyForcibly();
                    throw new RuntimeException("unable to run " + args.get(0) + " as it has timed-out");
                }
            }
            catch(InterruptedException ie) {
                throw new RuntimeException("interrupted waiting for the " + args.get(0) + " process to complete", ie);
            }

            // check the difference between the source and the destination.

            LOGGER.debug(
                    "did finish {} ({}ms)",
                    args.get(0),
                    (System.currentTimeMillis() - startMs));

            Files.asByteSource(temporaryOutputFile).copyTo(outputSink);

        }
        finally {
            quietlyDeleteTemporaryFile(temporaryInputFile);
            quietlyDeleteTemporaryFile(temporaryOutputFile);
        }

    }

}
