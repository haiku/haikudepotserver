/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import io.avaje.config.Config;
import io.avaje.inject.Component;
import org.haiku.haikudepotserver.graphics.model.Tool;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

@Component
public class ToolService {

    private final File hvif2pngTool;

    private final File pngquantTool;

    private final File convertTool;
    private final String convertLimitMemory;
    private final String convertLimitDisk;

    private final File oxipngTool;

    private final boolean quantize;

    public ToolService() {

        hvif2pngTool = new File(Config.get(Constants.KEY_CONFIG_HVIF2PNG_PATH));
        pngquantTool = new File(Config.get(Constants.KEY_CONFIG_PNGQUANT_PATH, "/usr/bin/pngquant"));
        convertTool = new File(Config.get(Constants.KEY_CONFIG_CONVERT_PATH, "/usr/bin/convert"));
        convertLimitMemory = Config.get(Constants.KEY_CONFIG_CONVERT_LIMIT_MEMORY);
        convertLimitDisk = Config.get(Constants.KEY_CONFIG_CONVERT_LIMIT_DISK);
        oxipngTool = new File(Config.get(Constants.KEY_CONFIG_OXIPNG_PATH));
        quantize = Config.getBool(Constants.KEY_CONFIG_QUANTIZE, false);

        List<String> missingFilenames = Stream.of(hvif2pngTool, pngquantTool, convertTool, oxipngTool)
                .filter(f -> !f.isFile())
                .map(Object::toString)
                .toList();

        if (!missingFilenames.isEmpty()) {
            throw new IllegalStateException("the tools [" + String.join(",", missingFilenames)
                    + "] do not exist");
        }
    }

    private Tool getHvif2pngTool(int size) {
        return new Tool(new String[] { hvif2pngTool.getAbsolutePath(), "-s", Integer.toString(size) });
    }

    private Tool getPngquantTool() {
        return new Tool(new String[] { pngquantTool.getAbsolutePath(), "-q", "90", "-" });
    }

    private Tool getConvertThumbnailTool(int width, int height) {
        return new Tool(
                new String[]{
                        convertTool.getAbsolutePath(),
                        "-",
                        "-limit", "memory", convertLimitMemory,
                        "-limit", "disk", convertLimitDisk,
                        "-thumbnail",
                        String.format("%dx%d>", width, height),
                        "png:-"
                }
        );
    }

    private Tool getOxipngTool() {
        return new Tool(new String[]{oxipngTool.getAbsolutePath(), "-"});
    }

    public Tool[] getHvif2pngToolsPipeline(int size) {
        return new Tool[] {getHvif2pngTool(size)};
    }

    public Tool[] getOptimizeToolsPipeline() {
        if (quantize) {
            return new Tool[]{getPngquantTool()};
        }

        return new Tool[] {getOxipngTool()};
    }

    public Tool[] getThumbnailToolsPipeline(int width, int height) {
        if (quantize) {
            return new Tool[]{
                    getConvertThumbnailTool(width, height),
                    getPngquantTool(),
            };
        }

        return new Tool[]{getConvertThumbnailTool(width, height)};
    }

}
