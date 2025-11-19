/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import org.haiku.haikudepotserver.graphics.support.ToolHelper;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@Validated
@Controller
public class OptimizeController {

    private final ToolService toolService;

    public OptimizeController(ToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * <p>Reads the input as an image and then optimizes it.</p>
     */

    @PostMapping("/" + Constants.SEGMENT_GRAPHICS + "/optimize")
    public ResponseEntity<StreamingResponseBody> optimize(InputStream stream) {
        return ToolHelper.runToolsPipelineWithPermitsForController(
                null,
                toolService.getOptimizeToolsPipeline(),
                stream,
                ToolHelper.pngHttpHeaders()
        );
    }


}
