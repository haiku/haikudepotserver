/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.bitmap;

import java.io.IOException;

public interface PngThumbnailService {

    /**
     * <p>This method will produce a thumbnail of a PNG image.</p>
     * @param width is the size of the box into which the thumbnail should be contained.
     * @param height is the size of the box into which the thumbnail should be contained.
     * @return a PNG image containing the thumbnail data.
     */

    // TODO; change to io-stream interface
    byte[] thumbnail(byte[] input, int width, int height) throws IOException;

}
