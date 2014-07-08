/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.passwordreset.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>This services a GET request that redirects to the single-page environment and that will provide a user
 * interface that allows the user to reset the password based on the supplied token.</p>
 */

@Controller
public class PasswordResetController {

    public final static String KEY_TOKEN = "token";

    @RequestMapping(value = "/passwordreset/{"+KEY_TOKEN+"}", method = RequestMethod.GET)
    public ModelAndView handleGet(
            HttpServletResponse response,
            @PathVariable(value = KEY_TOKEN) String token)
            throws IOException {
        return new ModelAndView("redirect:/#/completepasswordreset/"+token);
    }

}
