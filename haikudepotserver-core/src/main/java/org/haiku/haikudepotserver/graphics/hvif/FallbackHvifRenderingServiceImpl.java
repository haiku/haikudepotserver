/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.hvif;

import org.haiku.haikudepotserver.graphics.ImageHelper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;

/**
 * <p>This rendering service will render the HVIF as a "placeholder" bitmap.  It is a dummy
 * implementation for when there is no "hvif2png" tool available.</p>
 */

class FallbackHvifRenderingServiceImpl implements HvifRenderingService {

    private final Random random = new Random();

    private final ImageHelper imageHelper = new ImageHelper();

    private byte[] generic(int size) throws IOException {

        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            imageHelper.createFilledPng(
                    output,
                    size,
                    size,
                    random.nextInt(0, 256),
                    random.nextInt(0, 256),
                    random.nextInt(0, 256));
            return output.toByteArray();
        }
    }

    @Override
    public byte[] render(int size, byte[] input) throws IOException {
        return generic(16==size || 32==size || 48==size ? size : 64);
    }
}
