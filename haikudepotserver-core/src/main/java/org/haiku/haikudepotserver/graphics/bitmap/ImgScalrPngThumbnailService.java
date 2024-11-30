/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics.bitmap;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.imgscalr.Scalr;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * <p>This implementation uses the "ImgScalar" library in-memory to produce thumbnail images
 * of larger PNG images.</p>
 */

public class ImgScalrPngThumbnailService implements PngThumbnailService {

    private final ImageHelper imageHelper = new ImageHelper();

    @Override
    public byte[] thumbnail(byte[] input, int width, int height) throws IOException {
        Preconditions.checkArgument(null != input);
        Preconditions.checkArgument(width > 0, "width  must be greater than 0");
        Preconditions.checkArgument(height > 0, "height must be greater than 0");

        ImageHelper.Size size = imageHelper.derivePngSize(input);

        if (null == size) {
            throw new IOException("unable to derive size for png image");
        }

        // check to see if the screenshot needs to be resized to fit.
        if (size.width > width || size.height > height) {
            try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                ByteArrayInputStream imageInputStream = new ByteArrayInputStream(input);
                BufferedImage bufferedImage = ImageIO.read(imageInputStream);
                BufferedImage scaledBufferedImage = Scalr.resize(bufferedImage, width, height);
                ImageIO.write(scaledBufferedImage, "png", outputStream);
                return outputStream.toByteArray();
            }
        }
        return input;
    }

}
