/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.singlepage.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.ModelAndView;

/**
 * <p>This controller renders the default HTML entry point into the application.  As this is <em>generally</em> a
 * single page application, this controller will just render that single page.</p>
 */

@Controller
@RequestMapping("/")
public class EntryPointController {

    @RequestMapping(method = RequestMethod.GET)
    public ModelAndView entryPoint(HttpServletRequest httpServletRequest) {
        ModelAndView result = new ModelAndView("singlepage/launch");
        result.addObject("request", httpServletRequest);
        return result;
    }

}
