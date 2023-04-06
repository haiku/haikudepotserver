/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.monofont;

import com.google.common.base.Preconditions;

import java.util.BitSet;

/**
 * <p>This class represents a character in a bitmap font.</p>
 */

public class BitmapCharacter {

    private final BitSet pixels;
    private final int width;
    private final int height;

    public BitmapCharacter(BitSet pixels, int width, int height) {
        Preconditions.checkArgument(width > 0, "the width must be greater than zero");
        Preconditions.checkArgument(height > 0, "the height must be greater than zero");
        this.pixels = new BitSet();
        this.pixels.or(pixels);
        this.width = width;
        this.height = height;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public boolean get(int x, int y) {
        Preconditions.checkArgument(x >= 0 || x < width, "the x coordinate is out of bounds");
        Preconditions.checkArgument(y >= 0 || y < height, "the y coordinate is out of bounds");
        return pixels.get(y * width + x);
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        for (int i=0; i<height; i++) {
            if (!result.isEmpty()) {
                result.append('\n');
            }
            for (int j = 0; j < width; j++) {
                result.append(get(j, i) ? '*' : '.');
            }
        }

        return result.toString();
    }

}
