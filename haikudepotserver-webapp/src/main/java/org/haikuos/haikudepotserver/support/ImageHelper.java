/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support;

import com.google.common.base.Preconditions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ImageHelper {

    protected static Logger logger = LoggerFactory.getLogger(ImageHelper.class);

    private int MAGIC[] = {
            0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private int IHDR[] = {
            0x49, 0x48, 0x44, 0x52
    };

    /**
     * <p>This method will read the first few bytes of a PNG image and will return the size.  It will return
     * NULL if this does not appear to be a PNG image.</p>
     */

    public Size derivePngSize(byte[] data) {
        Preconditions.checkNotNull(data);

        if(data.length < 8 + 4 + 4 + 4 + 4) {
            return null;
        }

        // check for the magic header.

        for(int i=0;i<MAGIC.length;i++) {
            if((int) (0xff & data[i]) != MAGIC[i]) {
                logger.trace("the magic header is not present in the png data");
                return null;
            }
        }

        //check the lenth.

        int length = parseInt32(data, 8);

        // check for the expected first chunk header.

        for(int i=0;i<IHDR.length;i++) {
            if((int) (0xff & data[12+i]) != IHDR[i]) {
                logger.trace("the IHDR chunk is not present in the png data");
                return null;
            }
        }

        // now get the width and height.

        Size size = new Size();
        size.width = parseInt32(data,16);
        size.height = parseInt32(data,20);

        return size;
    }

    private int parseInt32(byte[] data, int offset) {
        return
                ((int) (0xff & data[offset])) << 24
                        | ((int) (0xff & data[offset+1])) << 16
                        | ((int) (0xff & data[offset+2])) << 8
                        | ((int) (0xff & data[offset+3]));
    }

    public static class Size {
        public int width;
        public int height;

        public boolean areSides(int s) {
            return s==width && s==height;
        }

        public String toString() {
            return String.format("{%d,%d}",width,height);
        }
    }

}
