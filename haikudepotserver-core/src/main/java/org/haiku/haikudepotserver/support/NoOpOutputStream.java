/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>This output stream will just consume and discard all of the bytes written to it.
 * This is useful for getting a count of the number of bytes output for example.</p>
 */

public class NoOpOutputStream extends OutputStream {

    @Override
    public void write(int b) throws IOException {
        // do nothing
    }

    @Override
    public void write(byte[] b) throws IOException {
        // do nothing
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        // do nothing
    }

}
