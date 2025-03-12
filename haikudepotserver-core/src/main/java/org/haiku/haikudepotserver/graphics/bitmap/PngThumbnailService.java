/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.bitmap;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface PngThumbnailService {

    /**
     * <p>This method will produce a thumbnail of a PNG image.</p>
     * @param width is the size of the box into which the thumbnail should be contained.
     * @param height is the size of the box into which the thumbnail should be contained.
     */

    void thumbnail(InputStream input, OutputStream output, int width, int height) throws IOException;

}
