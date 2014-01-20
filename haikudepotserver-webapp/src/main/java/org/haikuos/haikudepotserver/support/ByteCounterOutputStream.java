/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/**
 * <p>This output stream filter can be used to keep a count of the number of bytes that are
 * written to the output.</p>
 */

public class ByteCounterOutputStream extends FilterOutputStream {

    private long counter = 0;

    private boolean amWriting = false;

    public ByteCounterOutputStream(OutputStream outputStream) {
        super(outputStream);
    }

    @Override
    public void write(int b) throws IOException {
        if(!amWriting) {
            amWriting = true;
            counter++;
            super.write(b);
            amWriting = false;
        }
        else {
            super.write(b);
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        if(!amWriting) {
            amWriting = true;
            counter+=b.length;
            super.write(b);
            amWriting = false;
        }
        else {
            super.write(b);
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if(!amWriting) {
            amWriting = true;
            counter+=len;
            super.write(b,off,len);
            amWriting = false;
        }
        else {
            super.write(b,off,len);
        }
    }

    public long getCounter() {
        return counter;
    }

}
