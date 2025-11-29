/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import io.avaje.http.api.Controller;
import io.avaje.http.api.Post;
import io.avaje.http.api.Produces;
import io.avaje.http.api.StreamingOutput;
import org.haiku.haikudepotserver.graphics.support.ToolHelper;

import java.io.IOException;
import java.io.InputStream;

//@Validated
@Controller("/" + Constants.SEGMENT_GRAPHICS)
public class OptimizeController {

    private final ToolService toolService;

    public OptimizeController(ToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * <p>Reads the input as an image and then optimizes it.</p>
     */

    @Post("optimize")
    @Produces(value = Constants.MEDIA_TYPE_PNG, statusCode = 200)
    public StreamingOutput optimize(
            InputStream inputStream
    ) throws IOException {
        return ToolHelper.runToolsPipelineWithPermitsAsStreamingOutput(
                null,
                toolService.getOptimizeToolsPipeline(),
                inputStream
        );
    }


}
