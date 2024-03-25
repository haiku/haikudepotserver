/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.controller;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.reference.job.ReferenceDumpExportJobRunner;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.haiku.haikudepotserver.support.IntArrayVersionComparator;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.desktopapplication.DesktopApplicationHelper;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;

@Controller
@RequestMapping(path = {"__reference"})
public class ReferenceController extends AbstractController {

    private final static String KEY_NATURALLANGUAGECODE = "naturalLanguageCode";

    @Deprecated
    private final static int[] VERSION_MINIMUM_COMPLEX_LANGUAGES = new int[] { 0, 0, 8 };

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
            @RequestHeader(value = HttpHeaders.IF_MODIFIED_SINCE, required = false) String ifModifiedSinceHeader,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgentHeader)
            throws IOException {
        ReferenceDumpExportJobSpecification specification = new ReferenceDumpExportJobSpecification();
        specification.setNaturalLanguageCode(naturalLanguageCode);

        if (requiresSimpleTwoCharacterLanguageCodes(userAgentHeader)) {
            specification.setFilterForSimpleTwoCharLanguageCodes(true);
        }

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

    /**
     * <p>Temporary measure as older HaikuDepot clients are not able to properly cope with more complex language codes.
     * </p>
     */

    @Deprecated
    private boolean requiresSimpleTwoCharacterLanguageCodes(String userAgentHeader) {
        IntArrayVersionComparator comparator = new IntArrayVersionComparator();
        return Optional.of(StringUtils.trimToNull(userAgentHeader))
                .filter(DesktopApplicationHelper::matchesUserAgent)
                .flatMap(DesktopApplicationHelper::tryDeriveVersionFromUserAgent)
                .filter(version -> comparator.compare(version, VERSION_MINIMUM_COMPLEX_LANGUAGES) < 0)
                .isPresent();
    }

}
