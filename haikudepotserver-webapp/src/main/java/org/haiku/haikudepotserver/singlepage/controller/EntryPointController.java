/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.singlepage.controller;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.singlepage.SinglePageTemplateFrequencyMetrics;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.List;

/**
 * <p>This controller renders the default HTML entry point into the application.  As this is <em>generally</em> a
 * single page application, this controller will just render that single page.</p>
 */

@Controller
@RequestMapping("/")
public class EntryPointController {

    private final static int MAX_TOPTEMPLATES = 25;

    private final SinglePageTemplateFrequencyMetrics singlePageTemplateFrequencyMetrics;

    public EntryPointController(SinglePageTemplateFrequencyMetrics singlePageTemplateFrequencyMetrics) {
        this.singlePageTemplateFrequencyMetrics = Preconditions.checkNotNull(singlePageTemplateFrequencyMetrics);
    }

    @ModelAttribute("topTemplates")
    public List<String> getTopTemplates() {
        return singlePageTemplateFrequencyMetrics.top(MAX_TOPTEMPLATES);
    }

    @RequestMapping(method = RequestMethod.GET)
    public String entryPoint() {
        return "singlepage/launch";
    }

}
