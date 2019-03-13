/*
 * Copyright 2019, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.user.controller;

import com.google.common.base.Charsets;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;

@Controller
public class UserController {

    public final static String SEGMENT_USER = "__user";

    private final static String KEY_CODE = "code";

    private final static String LATEST = "latest";

    private final ServerRuntime serverRuntime;

    public UserController(
            ServerRuntime serverRuntime) {
        this.serverRuntime = serverRuntime;
    }

    @RequestMapping(
            value = "/" + SEGMENT_USER + "/usageconditions/{" + KEY_CODE + "}/document.md",
            method = RequestMethod.GET)
    public void handleGetUserUsageConditions(
            HttpServletResponse response,
            @PathVariable(value = KEY_CODE) String code) throws IOException {

        ObjectContext context = serverRuntime.newContext();
        UserUsageConditions userUsageConditions;

        if (LATEST.equals(code)) {
            userUsageConditions = UserUsageConditions.getLatest(context);
        } else {
            userUsageConditions = UserUsageConditions.getByCode(context, code);
        }

        response.setContentType(MediaType.MEDIATYPE_MARKDOWN);
        byte[] payload = userUsageConditions.getCopyMarkdown().getBytes(Charsets.UTF_8);
        response.setContentLength(payload.length);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(payload);
        }
    }

}
