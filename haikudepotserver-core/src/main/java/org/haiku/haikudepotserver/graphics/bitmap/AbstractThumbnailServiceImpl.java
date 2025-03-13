package org.haiku.haikudepotserver.graphics.bitmap;

import com.google.common.base.Preconditions;
import com.google.common.io.ByteStreams;
import org.haiku.haikudepotserver.graphics.ImageHelper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PushbackInputStream;

public abstract class AbstractThumbnailServiceImpl implements PngThumbnailService {

    private final ImageHelper imageHelper = new ImageHelper();

    @Override
    public void thumbnail(InputStream input, OutputStream output, int width, int height) throws IOException {
        Preconditions.checkArgument(null != input);
        Preconditions.checkArgument(null != output);
        Preconditions.checkArgument(width > 0, "width  must be greater than 0");
        Preconditions.checkArgument(height > 0, "height must be greater than 0");

        PushbackInputStream pushbackInputStream = new PushbackInputStream(input, 24);
        byte[] dataHeader = new byte[24];
        ByteStreams.readFully(pushbackInputStream, dataHeader);
        pushbackInputStream.unread(dataHeader);

        // checks to make sure that the image is actually a PNG.

        ImageHelper.Size size = imageHelper.derivePngSize(dataHeader);

        if (null == size) {
            throw new IOException("unable to derive size for png image");
        }

        // check to see if the screenshot needs to be resized to fit.
        if (size.width <= width || size.height <= height) {
            pushbackInputStream.transferTo(output);
        } else {
            thumbnailIgnoringExistingSizes(pushbackInputStream, output, width, height);
        }
    }

    protected abstract void thumbnailIgnoringExistingSizes(InputStream input, OutputStream output, int width, int height) throws IOException;

}
