/*
 * Copyright 2013, Andrew Lindesay
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

    private byte[] getPngData() throws IOException {
        InputStream inputStream = null;

        try {
            inputStream = this.getClass().getResourceAsStream("/sample-260x16.png");
            return ByteStreams.toByteArray(inputStream);
        }
        finally {
            Closeables.closeQuietly(inputStream);
        }
    }

    @Test
    public void testDerivePngSize() throws Exception {
        byte[] png = getPngData();
        ImageHelper imageHelper = new ImageHelper();
        ImageHelper.Size size = imageHelper.derivePngSize(png);
        assertThat(size).isNotNull();
        assertThat(size.width).isEqualTo(260);
        assertThat(size.height).isEqualTo(16);
    }

}
