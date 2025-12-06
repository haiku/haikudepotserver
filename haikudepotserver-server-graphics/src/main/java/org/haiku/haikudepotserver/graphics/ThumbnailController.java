/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import io.avaje.config.Config;
import io.avaje.http.api.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.haiku.haikudepotserver.graphics.support.ToolHelper;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Semaphore;

//@Validated
@Controller("/" + Constants.SEGMENT_GRAPHICS)
public class ThumbnailController {

    private final ToolService toolService;

    private final Semaphore semaphore;

    public ThumbnailController(
            ToolService toolService
    ) {
        this.toolService = toolService;
        this.semaphore = new Semaphore(Config.getInt(Constants.KEY_CONFIG_THUMBNAIL_PERMITS));
    }

    /**
     * <p>Reads the input as an image and then processes it to produce a thumbnail. The
     * only argument is the dimensions of the thumbnail.</p>
     */

    @Post("thumbnail")
    @Produces(value = Constants.MEDIA_TYPE_PNG, statusCode = 200)
    public StreamingOutput thumbnail(
            InputStream inputStream,
            @QueryParam(Constants.KEY_WIDTH) @Min(1) @Max(Constants.MAX_SIZE) Integer width,
            @QueryParam(Constants.KEY_HEIGHT) @Min(1) @Max(Constants.MAX_SIZE) Integer height
    ) throws IOException {
        return ToolHelper.runToolsPipelineWithPermitsAsStreamingOutput(
                semaphore,
                toolService.getThumbnailToolsPipeline(width, height),
                inputStream
        );
    }

}
