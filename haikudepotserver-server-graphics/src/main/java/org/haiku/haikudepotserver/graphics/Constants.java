/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

public class Constants {

    public final static String SEGMENT_GRAPHICS = "__gfx";

    /**
     * <p>This is the key for a query parameter that specifies the size of the
     * output images.</p>
     */

    public final static String KEY_SIZE = "sz";

    /**
     * <p>This is the key for specifying the width of an image.</p>
     */

    public final static String KEY_WIDTH = "w";

    /**
     * <p>This is the key for specifying the height of an image.</p>
     */

    public final static String KEY_HEIGHT = "h";

    /**
     * <p>This is a maximum size of the image output so that it does not exceed
     * a reasonable quantity of memory.</p>
     */

    public final static int MAX_SIZE = 1600;

}
