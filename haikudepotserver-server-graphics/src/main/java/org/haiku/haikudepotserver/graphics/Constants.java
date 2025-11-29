/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import java.io.File;

public class Constants {

    public final static long TIMEOUT_TOOL_EXEC_SECONDS = 10;

    public final static long TIMEOUT_ACQUIRE_PERMIT_SECONDS = 10;

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
     * <p>Maximum size of a request that the server will accept.</p>
     */

    public final static long MAX_REQUEST_SIZE = 10 * 1024 * 1024;

    /**
     * <p>This is a maximum size of the image output so that it does not exceed
     * a reasonable quantity of memory.</p>
     */

    public final static int MAX_SIZE = 1600;

    public final static String MEDIA_TYPE_PNG = "image/png";

    public final static String KEY_CONFIG_HVIF2PNG_PERMITS = "hds.gfx.controller.hvif2png.permits";
    public final static String KEY_CONFIG_THUMBNAIL_PERMITS = "hds.gfx.controller.thumbnail.permits";
    public final static String KEY_CONFIG_HVIF2PNG_PATH = "hds.tool.hvif2png.path";
    public final static String KEY_CONFIG_PNGQUANT_PATH = "hds.tool.pngquant.path";
    public final static String KEY_CONFIG_CONVERT_PATH = "hds.tool.convert.path";
    public final static String KEY_CONFIG_CONVERT_LIMIT_MEMORY = "hds.tool.convert.limit-memory";
    public final static String KEY_CONFIG_CONVERT_LIMIT_DISK = "hds.tool.convert.limit-disk";
    public final static String KEY_CONFIG_OXIPNG_PATH = "hds.tool.oxipng.path";
    public final static String KEY_CONFIG_QUANTIZE = "hds.gfx.quantize";
    public final static String KEY_CONFIG_SERVER_PORT = "server.port";

}
