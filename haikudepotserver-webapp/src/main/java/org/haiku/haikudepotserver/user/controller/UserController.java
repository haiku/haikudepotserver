/*
 * Copyright 2019-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.user.controller;

import com.google.common.base.Charsets;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Document;
import com.vladsch.flexmark.util.data.DataHolder;
import com.vladsch.flexmark.util.data.MutableDataSet;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.MediaType;
import org.haiku.haikudepotserver.dataobjects.UserUsageConditions;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.Optional;

@Controller
public class UserController {

    public final static String SEGMENT_USER = "__user";

    private final static String KEY_CODE = "code";

    private final static String LATEST = "latest";

    private final ServerRuntime serverRuntime;

    private final Parser markdownParser;

    private final HtmlRenderer htmlRenderer;

    public UserController(
            ServerRuntime serverRuntime) {
        this.serverRuntime = serverRuntime;
        this.markdownParser = Parser.builder(createMarkdownOptions()).build();
        this.htmlRenderer = HtmlRenderer.builder(createMarkdownOptions()).build();
    }

    @RequestMapping(
            value = "/" + SEGMENT_USER + "/usageconditions/{" + KEY_CODE + "}/document.md",
            method = RequestMethod.GET)
    public void handleGetUserUsageConditionsMarkdown(
            HttpServletResponse response,
            @PathVariable(value = KEY_CODE) String code) throws IOException {

        Optional<UserUsageConditions> markdownOptional = tryGetUserUsageConditions(code);

        if (markdownOptional.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        byte[] payload = markdownOptional.get().getCopyMarkdown().getBytes(Charsets.UTF_8);
        response.setContentType(MediaType.MEDIATYPE_MARKDOWN);
        response.setContentLength(payload.length);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(payload);
        }
    }

    @RequestMapping(
            value = "/" + SEGMENT_USER + "/usageconditions/{" + KEY_CODE + "}/document.html",
            method = RequestMethod.GET)
    public void handleGetUserUsageConditionsHtml(
            HttpServletResponse response,
            @PathVariable(value = KEY_CODE) String code) throws IOException {

        Optional<UserUsageConditions> markdownOptional = tryGetUserUsageConditions(code);

        if (markdownOptional.isEmpty()) {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        Document markdownDocument = markdownParser.parse(markdownOptional.get().getCopyMarkdown());
        byte[] payload = htmlRenderer.render(markdownDocument).getBytes(Charsets.UTF_8);

        response.setContentType(com.google.common.net.MediaType.HTML_UTF_8.toString());
        response.setContentLength(payload.length);
        try (OutputStream outputStream = response.getOutputStream()) {
            outputStream.write(payload);
        }
    }

    private Optional<UserUsageConditions> tryGetUserUsageConditions(String code) {
        ObjectContext context = serverRuntime.newContext();

        if (LATEST.equals(code)) {
            return Optional.of(UserUsageConditions.getLatest(context));
        }

        return UserUsageConditions.tryGetByCode(context, code);
    }

    private DataHolder createMarkdownOptions() {
        MutableDataSet options = new MutableDataSet();
        options.set(Parser.EXTENSIONS, List.of(TablesExtension.create()));
        return options;
    }

}
