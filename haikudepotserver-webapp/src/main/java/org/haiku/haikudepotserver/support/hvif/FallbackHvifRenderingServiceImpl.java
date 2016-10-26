/*
 * Copyright 2015-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.hvif;

import com.google.common.io.ByteStreams;

import java.io.IOException;
import java.io.InputStream;

/**
 * <p>This rendering service will render the HVIF as a "placeholder" bitmap.  It is a dummy
 * implementation for when there is no "hvif2png" tool available.</p>
 */

class FallbackHvifRenderingServiceImpl implements HvifRenderingService {

    private byte[] generic(int size) throws IOException {
        String resourcePath = String.format("/img/generic%derror.png",size);
        try (InputStream inputStream = FallbackHvifRenderingServiceImpl.class.getResourceAsStream(resourcePath)) {
            if(null==inputStream) {
                throw new IllegalStateException("unable to find the fallback resource; " + resourcePath);
            }

            return ByteStreams.toByteArray(inputStream);
        }
    }

    @Override
    public byte[] render(int size, byte[] input) throws IOException {
        return generic(16==size||32==size||48==size ? size : 64);
    }
}
