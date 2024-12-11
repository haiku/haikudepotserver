/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import org.haiku.haikudepotserver.graphics.model.Tool;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;
import java.util.stream.Stream;

@Service
public class ToolService {

    private final File hvif2pngTool;

    private final File pngquantTool;

    private final File convertTool;

    private final File oxipngTool;

    private final boolean quantize;

    public ToolService(
            @Value("${hds.tool.hvif2png.path}") File hvif2pngTool,
            @Value("${hds.tool.pngquant.path:/usr/bin/pngquant}") File pngquantTool,
            @Value("${hds.tool.convert.path:/usr/bin/convert}") File convertTool,
            @Value("${hds.tool.oxipng.path}") File oxipngTool,
            @Value("${hds.gfx.quantize:true}") boolean quantize) {

        List<String> missingFilenames = Stream.of(hvif2pngTool, pngquantTool, convertTool, oxipngTool)
                .filter(f -> !f.isFile())
                .map(Object::toString)
                .toList();

        if (!missingFilenames.isEmpty()) {
            throw new IllegalStateException("the tools [" + String.join(",", missingFilenames)
                    + "] do not exist");
        }

        this.hvif2pngTool = hvif2pngTool;
        this.pngquantTool = pngquantTool;
        this.convertTool = convertTool;
        this.oxipngTool = oxipngTool;
        this.quantize = quantize;
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
