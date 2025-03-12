/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics.bitmap;

import java.util.Arrays;

/**
 * <p>A simple indexed colour bitmap object for helping with the
 * assembly of images.</p>
 */

@Deprecated
public class IndexBitmap {

    private final int width;
    private final int height;
    private final short[] pixels;

    public IndexBitmap(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new short[width * height];
    }

    public short get(int x, int y) {
        return pixels[y * width + x];
    }

    public void set(int x, int y, short value) {
        pixels[y * width + x] = value;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public void fill(short value) {
        Arrays.fill(pixels, value);
    }
}
