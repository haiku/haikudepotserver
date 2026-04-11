/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.reference.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.BulkDataJobCoordinatorService;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.haiku.haikudepotserver.naturallanguage.model.NaturalLanguageCoordinates;
import org.haiku.haikudepotserver.reference.job.ReferenceDumpExportJobRunner;
import org.haiku.haikudepotserver.reference.model.ReferenceDumpExportJobSpecification;
import org.haiku.haikudepotserver.support.ControllerHelper;
import org.haiku.haikudepotserver.support.IntArrayVersionComparator;
import org.haiku.haikudepotserver.support.RuntimeInformationService;
import org.haiku.haikudepotserver.support.desktopapplication.DesktopApplicationHelper;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping(path = {"__reference"})
public class ReferenceController extends AbstractController {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReferenceController.class);

    private final static String KEY_NATURALLANGUAGECODE = "naturalLanguageCode";

    @Deprecated
    private final static int[] VERSION_MINIMUM_COMPLEX_LANGUAGES = new int[] { 0, 0, 8 };

    private final RuntimeInformationService runtimeInformationService;
    private final BulkDataJobCoordinatorService bulkDataJobCoordinatorService;
    private final ServerRuntime serverRuntime;
    private final JobService jobService;

    public ReferenceController(
            RuntimeInformationService runtimeInformationService,
            BulkDataJobCoordinatorService bulkDataJobCoordinatorService,
            ServerRuntime serverRuntime,
            JobService jobService) {
        this.runtimeInformationService = runtimeInformationService;
        this.bulkDataJobCoordinatorService = bulkDataJobCoordinatorService;
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

        NaturalLanguageCoordinates naturalLanguage = NaturalLanguageCoordinates.fromCode(naturalLanguageCode);

        if (requiresSimpleTwoCharacterLanguageCodes(userAgentHeader)) {
            getAllAsJsonLegacy(response, naturalLanguageCode, ifModifiedSinceHeader);
        } else {
            String jobCode = bulkDataJobCoordinatorService.getOrCreateReferenceDumpExport(naturalLanguage);

            ControllerHelper.maybeRedirectToJobData(
                    jobService,
                    response,
                    jobCode,
                    ifModifiedSinceHeader,
                    userAgentHeader
            );
        }
    }

    /**
     * <p>Some older clients < 0.0.8 are unable to cope with language codes < 2 characters. The server therefore
     * needs to be able to serve these people older data.</p>
     */
    // TODO (andponlin) remove this once clients 0.0.8 are no longer supported.
    @Deprecated
    private void getAllAsJsonLegacy(
            HttpServletResponse response,
            String naturalLanguageCode,
            String ifModifiedSinceHeader) throws IOException {

        ReferenceDumpExportJobSpecification jobSpecification = new ReferenceDumpExportJobSpecification();
        jobSpecification.setNaturalLanguageCode(naturalLanguageCode);
        jobSpecification.setProjectVersion(runtimeInformationService.getProjectVersion());
        jobSpecification.setFilterForSimpleTwoCharLanguageCodes(true); // <-- triggers legacy behaviour

        Date lastModifyTimestamp = ReferenceDumpExportJobRunner.getModifyTimestamp(
                serverRuntime.newContext(),
                runtimeInformationService
        );
        Date now = new Date(Clock.systemUTC().millis());

        if (lastModifyTimestamp.after(now)) {
            throw new IllegalStateException("the last modify timestamp (data) of ["
                    + lastModifyTimestamp + "] is after the current timestamp");
        }

        if (!Strings.isNullOrEmpty(ifModifiedSinceHeader)) {
            try {
                Date requestModifyTimestamp = new Date(Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSinceHeader)).toEpochMilli());

                if (requestModifyTimestamp.after(now)) {
                    LOGGER.warn("the supplied if modified since header [{}] is after the current time", requestModifyTimestamp);
                }
                else {
                    if (requestModifyTimestamp.getTime() >= lastModifyTimestamp.getTime()) {
                        response.setStatus(HttpStatus.NOT_MODIFIED.value());
                        return;
                    }
                }
            } catch (DateTimeParseException dtpe) {
                LOGGER.warn("bad [{}] header on request; [{}] -- will ignore",
                        HttpHeaders.IF_MODIFIED_SINCE,
                        StringUtils.abbreviate(ifModifiedSinceHeader, 128));
            }
        }

        // what happens here is that we get the report and if it is too old, delete it and try again.

        JobSnapshot jobSnapshot = getJobSnapshotStartedAfter(jobService, lastModifyTimestamp, jobSpecification);
        Set<String> jobDataGuids = jobSnapshot.getDataGuids();

        if (1 != jobDataGuids.size()) {
            throw new IllegalStateException("found [" + jobDataGuids.size() + "] job data guids related to the job ["
                    + jobSnapshot.getGuid() + "] - was expecting 1");
        }

        String lastModifiedValue = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.ofInstant(
                lastModifyTimestamp.toInstant(), ZoneOffset.UTC));
        String destinationLocationUrl = UriComponentsBuilder.newInstance()
                .pathSegment(WebConstants.PATH_COMPONENT_SECURED)
                .pathSegment(JobController.SEGMENT_JOBDATA)
                .pathSegment(jobDataGuids.iterator().next())
                .pathSegment(JobController.SEGMENT_DOWNLOAD)
                .toUriString();

        response.addHeader(HttpHeaders.LAST_MODIFIED, lastModifiedValue);
        response.sendRedirect(destinationLocationUrl);

    }

    @Deprecated
    private static JobSnapshot getJobSnapshotStartedAfter(
            JobService jobService,
            Date lastModifyTimestamp,
            JobSpecification jobSpecification) {
        for (int i = 0; i < 3; i++) {
            String jobGuid = jobService.immediate(jobSpecification, true);
            JobSnapshot jobSnapshot = jobService.tryGetJob(jobGuid)
                    .orElseThrow(() -> new IllegalStateException("unable to obtain the job snapshot having run it immediately prior."));

            if (jobSnapshot.getStartTimestamp().getTime() >= lastModifyTimestamp.getTime()) {
                return jobSnapshot;
            }

            jobService.removeJob(jobGuid); // remove the stale one.
        }

        throw new IllegalStateException("unable to find a job snapshot started after [" + lastModifyTimestamp + "]");
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
