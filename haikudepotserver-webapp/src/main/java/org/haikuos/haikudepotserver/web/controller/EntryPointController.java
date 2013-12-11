/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.web.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * <p>This controller renders the default HTML entry point into the application.  As this is <em>generally</em> a
 * single page application, this controller will just render that single page.</p>
 */

@Controller
@RequestMapping("/")
public class EntryPointController {

    @RequestMapping(method = RequestMethod.GET)
    public String entryPoint() {
        return "entryPoint";
    }
}
