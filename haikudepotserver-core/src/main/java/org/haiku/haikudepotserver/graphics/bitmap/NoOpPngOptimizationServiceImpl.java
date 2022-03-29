/*
 * Copyright 2015-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.bitmap;

/**
 * <p>Does nothing to the PNG to optimize it.</p>
 */

class NoOpPngOptimizationServiceImpl implements PngOptimizationService {

    public boolean identityOptimization() {
        return true;
    }

    @Override
    public byte[] optimize(byte[] input) {
        return input;
    }
}
