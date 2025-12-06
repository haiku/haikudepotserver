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
public class Hvif2pngController {

    private final ToolService toolService;

    private final Semaphore semaphore;

    public Hvif2pngController(ToolService toolService) {
        this.toolService = toolService;
        this.semaphore = new Semaphore(Config.getInt(Constants.KEY_CONFIG_HVIF2PNG_PERMITS));
    }

    /**
     * <p>Processes the input data from the request into the `hvif2png` tool and then
     * streams out the resultant PNG file.</p>
     */

    @Post("hvif2png")
    @Produces(value = Constants.MEDIA_TYPE_PNG, statusCode = 200)
    public StreamingOutput thumbnail(
            InputStream inputStream,
            @QueryParam(Constants.KEY_SIZE) @Min(1) @Max(Constants.MAX_SIZE) Integer size
    ) throws IOException {
        return ToolHelper.runToolsPipelineWithPermitsAsStreamingOutput(
                semaphore,
                toolService.getHvif2pngToolsPipeline(size),
                inputStream
        );
    }

}
