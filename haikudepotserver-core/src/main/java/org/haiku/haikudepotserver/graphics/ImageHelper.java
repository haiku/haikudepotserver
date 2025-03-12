/*
 * Copyright 2013-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics;

import ar.com.hjg.pngj.ImageInfo;
import ar.com.hjg.pngj.ImageLineInt;
import ar.com.hjg.pngj.PngWriter;
import ar.com.hjg.pngj.chunks.PngChunkPLTE;
import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

/**
 * <p>This class provides simple, static manipulations or inspections of image data.</p>
 */

public class ImageHelper {

    protected final static Logger LOGGER = LoggerFactory.getLogger(ImageHelper.class);

    private static final int[] HVIF_MAGIC = {
            0x6e, 0x63, 0x69, 0x66
    };

    private static final int[] PNG_MAGIC = {
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private static final int[] PNG_IHDR = {
            0x49, 0x48, 0x44, 0x52
    };

    /**
     * <p>Haiku Vector Icon Format (hvif) is a data format specific to the Haiku platform and is a way in which
     * icons can be stored very compactly in a vector format.  This method will return true if the data supplied
     * looks like hvif.</p>
     */

    public boolean looksLikeHaikuVectorIconFormat(byte[] data) {
        Preconditions.checkNotNull(data);

        if (data.length < 4) {
            return false;
        }

        for (int i = 0; i < HVIF_MAGIC.length; i++) {
            if ((0xff & data[i]) != HVIF_MAGIC[i]) {
                LOGGER.trace("the magic header is not present in the hvif data");
                return false;
            }
        }

        return true;
    }

    /**
     * <p>This method will read the first few bytes of a PNG image and will return the size.  It will return
     * NULL if this does not appear to be a PNG image.</p>
     */

    public Size derivePngSize(byte[] data) {
        Preconditions.checkNotNull(data);

        if (data.length < 8 + 4 + 4 + 4 + 4) {
            return null;
        }

        // check for the magic header.

        for (int i = 0; i < PNG_MAGIC.length; i++) {
            if ((0xff & data[i]) != PNG_MAGIC[i]) {
                LOGGER.trace("the magic header is not present in the png data");
                return null;
            }
        }

        //check the length.

        parseInt32(data, 8); // length

        // check for the expected first chunk header.

        for (int i = 0; i < PNG_IHDR.length; i++) {
            if ((0xff & data[12 + i]) != PNG_IHDR[i]) {
                LOGGER.trace("the IHDR chunk is not present in the png data");
                return null;
            }
        }

        // now get the width and height.

        Size size = new Size();
        size.width = parseInt32(data, 16);
        size.height = parseInt32(data, 20);

        return size;
    }

    private int parseInt32(byte[] data, int offset) {
        return
                (0xff & data[offset]) << 24
                        | (0xff & data[offset + 1]) << 16
                        | (0xff & data[offset + 2]) << 8
                        | (0xff & data[offset + 3]);
    }

    /**
     * <p>Produces a rectangle PNG of the specified size with the interior filled with the
     * colour specified.</p>
     */

    public void createFilledPng(OutputStream output, int width, int height, int r, int g, int b) throws IOException {
        ImageInfo imi = new ImageInfo(
                width, height,
                2, false, false, true);

        PngWriter png = new PngWriter(output, imi);

        png.getMetadata().setDpi(72);
        png.getMetadata().setTimeNow();

        PngChunkPLTE palette = png.getMetadata().createPLTEChunk();
        palette.setNentries(2);
        palette.setEntry(0, 0, 0, 0);
        palette.setEntry(1, r, g, b);

        ImageLineInt topBottomLine = new ImageLineInt(imi);
        Arrays.fill(topBottomLine.getScanline(), 0, width, 0);
        ImageLineInt middleLine = new ImageLineInt(imi);
        Arrays.fill(middleLine.getScanline(), 0, width, 1);
        middleLine.getScanline()[0] = 0;
        middleLine.getScanline()[width - 1] = 0;

        png.writeRow(topBottomLine);
        for (int i = 0; i < height - 2; i++)
            png.writeRow(middleLine);
        png.writeRow(topBottomLine);

        png.end();
    }

    public static class Size {
        public int width;
        public int height;

        public boolean areSides(int s) {
            return s == width && s == height;
        }

        public String toString() {
            return String.format("{%d,%d}", width, height);
        }
    }

}
