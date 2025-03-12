/*
 * Copyright 2015-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.bitmap;

import org.haiku.haikudepotserver.graphics.ImageHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Random;

/**
 * This will produce a placeholder image instead of the thumbnail of the actual image.
 */

public class FallbackThumbnailServiceImpl implements PngThumbnailService {

    private final Random random = new Random();

    private final ImageHelper imageHelper = new ImageHelper();

    @Override
    public void thumbnail(InputStream input, OutputStream output, int width, int height) throws IOException {
        imageHelper.createFilledPng(
                output,
                width,
                height,
                random.nextInt(0, 256),
                random.nextInt(0, 256),
                random.nextInt(0, 256));
    }

}
