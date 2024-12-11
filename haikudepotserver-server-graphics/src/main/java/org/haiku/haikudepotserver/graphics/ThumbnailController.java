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
public class ThumbnailController {

    private final ToolService toolService;

    public ThumbnailController(ToolService toolService) {
        this.toolService = toolService;
    }

    /**
     * <p>Reads the input as an image and then processes it to produce a thumbnail. The
     * only argument is the dimensions of the thumbnail.</p>
     */

    @PostMapping("/" + Constants.SEGMENT_GRAPHICS + "/thumbnail")
    public ResponseEntity<StreamingResponseBody> thumbnail(
            InputStream stream,
            @RequestParam(value = Constants.KEY_WIDTH) @Min(1) @Max(Constants.MAX_SIZE) Integer width,
            @RequestParam(value = Constants.KEY_HEIGHT) @Min(1) @Max(Constants.MAX_SIZE) Integer height) {

        StreamingResponseBody responseBody = (out) -> ToolHelper.runToolsPipeline(
                toolService.getThumbnailToolsPipeline(width, height),
                stream, out);

        return new ResponseEntity<>(responseBody, ToolHelper.pngHttpHeaders(), HttpStatus.OK);
    }

}
