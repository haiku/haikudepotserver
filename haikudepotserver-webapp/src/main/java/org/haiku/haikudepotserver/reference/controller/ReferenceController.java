/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.controller;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.reference.job.ReferenceDumpExportJobRunner;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping(path = {"__reference"})
public class ReferenceController extends AbstractController {

    private final static String KEY_NATURALLANGUAGECODE = "naturalLanguageCode";

    private final RuntimeInformationService runtimeInformationService;
    private final ServerRuntime serverRuntime;
    private final JobService jobService;

    public ReferenceController(
            RuntimeInformationService runtimeInformationService,
            ServerRuntime serverRuntime,
            JobService jobService) {
        this.runtimeInformationService = runtimeInformationService;
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    /**
     * <p>This streams back a redirect to report data that contains a JSON stream gzip-compressed
     * that describes some details of selected reference data.  This is used by clients to provide
     * drop-down lists of languages etc...
     */

    @RequestMapping(value = {
            "/all-{naturalLanguageCode}.json.gz"
    }, method = RequestMethod.GET)
    public void getAllAsJson(
            HttpServletResponse response,
            @PathVariable(value = KEY_NATURALLANGUAGECODE) String naturalLanguageCode,
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSinceHeader)
            throws IOException {
        ReferenceDumpExportJobSpecification specification = new ReferenceDumpExportJobSpecification();
        specification.setNaturalLanguageCode(naturalLanguageCode);

        JobController.handleRedirectToJobData(
                response,
                jobService,
                ifModifiedSinceHeader,
                ReferenceDumpExportJobRunner.getModifyTimestamp(
                        serverRuntime.newContext(),
                        runtimeInformationService
                ),
                specification);
    }

}
