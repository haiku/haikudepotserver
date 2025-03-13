/*
 * Copyright 2015-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.bitmap;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>Does nothing to the PNG to optimize it.</p>
 */

class NoOpPngOptimizationServiceImpl implements PngOptimizationService {

    public boolean identityOptimization() {
        return true;
    }

    @Override
    public void optimize(InputStream input, OutputStream output) throws IOException {
        input.transferTo(output);
    }
}
