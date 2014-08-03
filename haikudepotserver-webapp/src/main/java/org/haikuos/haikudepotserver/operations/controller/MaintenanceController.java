/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.operations.controller;

import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.haikuos.haikudepotserver.passwordreset.PasswordResetMaintenanceService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>This controller takes care of scheduling maintenance tasks.</p>
 */

@Controller
@RequestMapping("/maintenance")
public class MaintenanceController {

    @Resource
    PasswordResetMaintenanceService passwordResetMaintenanceService;

    /**
     * <p>This triggers medium-term maintenance tasks.  It is suggested that this might be triggered
     * hourly.</p>
     */

    @RequestMapping(value = "/mediumterm", method = RequestMethod.GET)
    public void fetch(
            HttpServletResponse response) throws IOException {

        passwordResetMaintenanceService.submit();

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
        response.getWriter().print(String.format("accepted request for medium term maintenance"));

    }

}
