/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.web.controller;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.io.ByteStreams;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.haikuos.haikudepotserver.support.Closeables;
import org.haikuos.haikudepotserver.web.model.WebResourceGroup;
import org.haikuos.haikudepotserver.web.model.WebResourceGroupService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * <p>Resources such as java-script files and a CSS files can be defined as part of the spring context and then
 * this controller is able to take those beans and then render them into a single HTTP download.  This avoids the
 * page from having to download dozens of small javascript files; it can just request them as one HTTP request.</p>
 */

@Controller
@RequestMapping("/webresourcegroup")
public class WebResourceGroupController implements ApplicationContextAware {

    protected static Logger logger = LoggerFactory.getLogger(WebResourceGroupController.class);

    public final static String KEY_CODE = "code";

    @javax.annotation.Resource
    WebResourceGroupService webResourceGroupService;

    private ApplicationContext applicationContext;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }

    @RequestMapping(value = "/{"+KEY_CODE+"}", method = RequestMethod.GET)
    public void fetch(
            HttpServletResponse response,
            @PathVariable(value = KEY_CODE) String code)
            throws IOException {

        if(Strings.isNullOrEmpty(code)) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
            response.getWriter().print(String.format("the code is required"));
        }
        else {
            Optional<WebResourceGroup> webResourceGroupOptional = webResourceGroupService.getWebResourceGroup(code);

            if(
                    !webResourceGroupOptional.isPresent() ||
                            null==webResourceGroupOptional.get().getResources() ||
                            0==webResourceGroupOptional.get().getResources().size()) {

                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.PLAIN_TEXT_UTF_8.toString());
                response.getWriter().print(String.format("unable to find script group; %s",code));

            }
            else {
                response.setStatus(HttpServletResponse.SC_OK);
                response.setHeader(HttpHeaders.CONTENT_TYPE, webResourceGroupOptional.get().getMimeType());
                OutputStream outputStream = null;

                try {

                    outputStream = response.getOutputStream();

                    for(String resource : webResourceGroupOptional.get().getResources()) {
                        Resource r = applicationContext.getResource(resource);
                        InputStream inputStream = null;

                        try {
                            inputStream = r.getInputStream();

                            if(null==inputStream) {
                                logger.error("unable to find the resource {} in the script group {}",resource,code);
                            }
                            else {
                                outputStream.write(new byte[] { 0x0d, 0x0d });
                                ByteStreams.copy(inputStream, outputStream);
                            }
                        }
                        finally {
                            Closeables.closeQuietly(inputStream);
                        }
                    }

                }
                finally {
                    Closeables.closeQuietly(outputStream);
                }
            }
        }
    }

}
