/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.operations.controller;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.haiku.haikudepotserver.maintenance.model.MaintenanceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>This controller takes care of scheduling maintenance tasks.  Invocations come into here from a CURL
 * or similar request driven by cron.  This controller should no longer be required
 * as cron-configured maintenance has been replaced with Spring-based scheduling inside
 * the application server.</p>
 */

@Deprecated
@Controller
@RequestMapping(path = {
        "/maintenance", // TODO; remove
        "/__maintenance" })
public class MaintenanceController {

    protected static Logger LOGGER = LoggerFactory.getLogger(MaintenanceController.class);

    private final MaintenanceService maintenanceService;

    public MaintenanceController(MaintenanceService maintenanceService) {
        this.maintenanceService = Preconditions.checkNotNull(maintenanceService);
    }

    /**
     * <p>This triggers daily tasks.</p>
     */

    @Deprecated
    @RequestMapping(value = "/daily", method = RequestMethod.GET)
    public void daily(
            HttpServletResponse response) throws IOException {
        maintenanceService.daily();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
        response.getWriter().print("accepted request for daily maintenance");

    }

    /**
     * <p>This triggers hourly tasks.</p>
     */

    @Deprecated
    @RequestMapping(path = { "/hourly" }, method = RequestMethod.GET)
    public void hourly(
            HttpServletResponse response) throws IOException {
        maintenanceService.hourly();
        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
        response.getWriter().print("accepted request for hourly maintenance");

    }

}
