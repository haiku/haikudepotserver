/*
 * Copyright 2014-2015, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.singlepage.controller;

import org.haiku.haikudepotserver.singlepage.SinglePageTemplateFrequencyMetrics;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>This controller renders the default HTML entry point into the application.  As this is <em>generally</em> a
 * single page application, this controller will just render that single page.</p>
 */

@Controller
@RequestMapping("/")
public class EntryPointController {

    private static int MAX_TOPTEMPLATES = 25;

    @Resource
    private SinglePageTemplateFrequencyMetrics singlePageTemplateFrequencyMetrics;

    @ModelAttribute("topTemplates")
    public List<String> getTopTemplates() {
        return singlePageTemplateFrequencyMetrics.top(MAX_TOPTEMPLATES);
    }

    @RequestMapping(method = RequestMethod.GET)
    public String entryPoint() {
        return "singlepage/launch";
    }

}
