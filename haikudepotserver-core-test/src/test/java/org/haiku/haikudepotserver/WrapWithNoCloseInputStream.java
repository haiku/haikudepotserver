/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>This is used in testing.</p>
 */

public class WrapWithNoCloseInputStream extends InputStream {

    public InputStream inputStream;

    public WrapWithNoCloseInputStream(InputStream inputStream) {
        this.inputStream = inputStream;
    }

    @Override
    public int read() throws IOException {
        return inputStream.read();
    }
}
