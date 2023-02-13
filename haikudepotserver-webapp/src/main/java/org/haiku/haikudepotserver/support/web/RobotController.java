/*
 * Copyright 2014-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.web;

import com.google.common.net.MediaType;
import org.haiku.haikudepotserver.multipage.MultipageConstants;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;

// https://developers.google.com/webmasters/control-crawl-index/docs/robots_txt

@Controller
public class RobotController {

    @RequestMapping(value = "/robots.txt", method = RequestMethod.GET)
    public void robotResponse(HttpServletResponse response) throws IOException {
        response.setContentType(MediaType.PLAIN_TEXT_UTF_8.toString());

        PrintWriter writer = response.getWriter();

        writer.print("user-agent: *\n");
        writer.print("allow: ");
        writer.print(MultipageConstants.PATH_MULTIPAGE);
        writer.print("\n");
    }

}
