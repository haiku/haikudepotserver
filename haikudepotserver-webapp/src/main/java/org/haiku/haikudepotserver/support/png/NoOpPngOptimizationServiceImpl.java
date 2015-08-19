/*
 * Copyright 2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.png;

import java.io.IOException;

/**
 * <p>Does nothing to the PNG to optimize it.</p>
 */

public class NoOpPngOptimizationServiceImpl implements PngOptimizationService {

    public boolean identityOptimization() {
        return true;
    }

    @Override
    public byte[] optimize(byte[] input) throws IOException {
        return input;
    }
}
