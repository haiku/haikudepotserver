/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.passwordreset.controller;

import org.haiku.haikudepotserver.passwordreset.PasswordResetServiceImpl;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;

/**
 * <p>This services a GET request that redirects to the single-page environment and that will provide a user
 * interface that allows the user to reset the password based on the supplied token.</p>
 */

@Controller
public class PasswordResetController {

    private final static String KEY_TOKEN = "token";

    // TODO; separate so that it is not configured from the orchestration service.
    private final static String SEGMENT_PASSWORDRESET = PasswordResetServiceImpl.URL_SEGMENT_PASSWORDRESET;

    @RequestMapping(value = "/" + SEGMENT_PASSWORDRESET + "/{"+KEY_TOKEN+"}", method = RequestMethod.GET)
    public ModelAndView handleGet(
            @PathVariable(value = KEY_TOKEN) String token)
            throws IOException {
        return new ModelAndView("redirect:/#/completepasswordreset/"+token);
    }

}
