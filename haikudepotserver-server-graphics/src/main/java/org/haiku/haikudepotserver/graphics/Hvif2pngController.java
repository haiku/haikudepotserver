/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.graphics;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.haiku.haikudepotserver.graphics.support.ToolHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@Validated
@Controller
public class Hvif2pngController {

    private final ToolService toolService;

    public Hvif2pngController(ToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * <p>Processes the input data from the request into the `hvif2png` tool and then
     * streams out the resultant PNG file.</p>
     */

    @PostMapping("/" + Constants.SEGMENT_GRAPHICS + "/hvif2png")
    public ResponseEntity<StreamingResponseBody> thumbnail(
            InputStream stream,
            @RequestParam(value = Constants.KEY_SIZE) @Min(1) @Max(Constants.MAX_SIZE) Integer size) {

        StreamingResponseBody responseBody = (out) -> ToolHelper.runToolsPipeline(
                toolService.getHvif2pngToolsPipeline(size),
                stream, out);

        return new ResponseEntity<>(responseBody, ToolHelper.pngHttpHeaders(), HttpStatus.OK);
    }

}
