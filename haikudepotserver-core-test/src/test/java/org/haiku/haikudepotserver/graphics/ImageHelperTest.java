/*
 * Copyright 2013-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.graphics;

import com.google.common.io.ByteStreams;
import org.haiku.haikudepotserver.graphics.ImageHelper;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;

import static org.fest.assertions.Assertions.assertThat;

public class ImageHelperTest {

    private byte[] getData(String leafname) throws IOException {
        try (InputStream inputStream = this.getClass().getResourceAsStream(leafname)) {
            if(null == inputStream) {
                throw new IllegalStateException("unable to find image for; " + leafname);
            }

            return ByteStreams.toByteArray(inputStream);
        }
    }

    private void assertImageSize(String leafname, int width, int height) throws IOException {
        byte[] png = getData(leafname);
        ImageHelper imageHelper = new ImageHelper();
        ImageHelper.Size size = imageHelper.derivePngSize(png);
        assertThat(size).isNotNull();
        assertThat(size.width).isEqualTo(width);
        assertThat(size.height).isEqualTo(height);
    }

    @Test
    public void testDerivePngSize() throws IOException {
        assertImageSize("/sample-260x16.png", 260, 16);
        assertImageSize("/sample-32x32.png", 32, 32);
        assertImageSize("/sample-16x16.png", 16, 16);
    }

    @Test
    public void testLooksLikeHaikuVectorImageFormat_true() throws IOException {
         byte[] data = getData("/sample.hvif");
        ImageHelper imageHelper = new ImageHelper();
        assertThat(imageHelper.looksLikeHaikuVectorIconFormat(data)).isTrue();
    }

    @Test
    public void testLooksLikeHaikuVectorImageFormat_false() throws IOException {
        byte[] data = getData("/sample-16x16.png");
        ImageHelper imageHelper = new ImageHelper();
        assertThat(imageHelper.looksLikeHaikuVectorIconFormat(data)).isFalse();
    }

}
