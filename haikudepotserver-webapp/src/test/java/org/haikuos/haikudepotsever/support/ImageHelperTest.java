/*
 * Copyright 2013-2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotsever.support;

import com.google.common.io.ByteStreams;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.support.ImageHelper;
import org.junit.Test;
import static org.fest.assertions.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

public class ImageHelperTest {

    private byte[] getPngData(String leafname) throws IOException {
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream(leafname);
            return ByteStreams.toByteArray(inputStream);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    private void assertImageSize(String leafname, int width, int height) throws IOException {
        byte[] png = getPngData(leafname);
        ImageHelper imageHelper = new ImageHelper();
        ImageHelper.Size size = imageHelper.derivePngSize(png);
        assertThat(size).isNotNull();
        assertThat(size.width).isEqualTo(width);
        assertThat(size.height).isEqualTo(height);
    }

    @Test
    public void testDerivePngSize() throws IOException {
        assertImageSize("/sample-260x16.png",260,16);
        assertImageSize("/sample-32x32.png",32,32);
        assertImageSize("/sample-16x16.png",16,16);
    }

}
