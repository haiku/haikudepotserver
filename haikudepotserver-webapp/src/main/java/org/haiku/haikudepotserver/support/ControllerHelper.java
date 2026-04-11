/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.controller.JobController;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.support.desktopapplication.DesktopApplicationHelper;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * <p>Helpers that are used from multiple controllers.</p>
 */

public class ControllerHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(ControllerHelper.class);

    /**
     * <p>This value is sent back to clients when the data for a {@link JobSnapshot} is not quite ready
     * yet. Client should wait this length of time and then try again.</p>
     */
    private static final int DELAY_JOB_DATA_NOT_READY_SECONDS = 5;

    /**
     * <p>For clients that don't respond to being told the data is not quite ready, we'll wait this
     * length of time for the data to be ready and then fail if it's not.</p>
     */
    private static final int JOB_COMPLETE_WAIT_TIME_SECONDS = 15;

    /**
     * <p>This version of the HaikuDepot desktop application which is able to handle the 503 HTTP response by pausing
     * and trying again later. Earlier versions of the desktop application won't do this.</p>
     */

    public static int[] VERSION_HD_503_RETRY = new int[]{0, 0, 10};

    /**
     * <p>Looks to see if the Job has completed. If it has completed then redirect the client to the endpoint where the
     * data can be downloaded. If the data is created after the <code>If-Modified-Since</code> HTTP header then let
     * the client know that downloading new data is not necessary.</p>
     *
     * <p>The result is expressed in the supplied {@code response} object.</p>
     */
    public static void maybeRedirectToJobData(
            JobService jobService,
            HttpServletResponse response,
            String jobCode,
            String ifModifiedSinceHeader,
            String userAgentHeader
    ) throws IOException {
        Preconditions.checkArgument(StringUtils.isNotBlank(jobCode), "job code must be provided");
        Preconditions.checkArgument(null != response, "servlet response must be provided");

        Optional<? extends JobSnapshot> jobSnapshotOptional = jobService.tryGetJob(jobCode);

        if (jobSnapshotOptional.isEmpty()) {
            LOGGER.debug("job [{}] not found", jobCode);
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        JobSnapshot jobSnapshot = jobSnapshotOptional.get();

        if (!ControllerHelper.isJobDataAfterModifiedSinceHeader(jobSnapshot, ifModifiedSinceHeader)) {
            LOGGER.info("data not modified since [{}]", ifModifiedSinceHeader);
            response.setStatus(HttpStatus.NOT_MODIFIED.value());
            return;
        }

        switch (jobSnapshot.getStatus()) {
            case QUEUED, STARTED:
                handleJobNotFinished(jobService, response, jobSnapshot, userAgentHeader);
                break;
            case FINISHED:
                LOGGER.info("data ready for [{}] --> redirect client to data", jobSnapshot.getGuid());
                respondRedirectToJobData(response, jobSnapshot);
                break;
            default:
                throw new IllegalStateException("unexpected job state [%s]".formatted(jobSnapshot.getStatus()));
        }
    }

    private static void handleJobNotFinished(
            JobService jobService,
            HttpServletResponse response,
            JobSnapshot jobSnapshot,
            String userAgentHeader
    ) throws IOException {

        if (!ControllerHelper.isUserAgentAbleToRetryOn503(userAgentHeader)) {
            handleJobNotFinishedLegacy(jobService, response, jobSnapshot);
        } else {
            LOGGER.info(
                    "data not ready --> requesting the client retry after {}s",
                    DELAY_JOB_DATA_NOT_READY_SECONDS);
            // TODO (andponlin) Could use information about the report to establish how long the report
            //  still has to run and use that timing to be more accurate for the client.
            response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(DELAY_JOB_DATA_NOT_READY_SECONDS));
            response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
        }
    }

    @Deprecated
    private static void handleJobNotFinishedLegacy(
            JobService jobService,
            HttpServletResponse response,
            JobSnapshot jobSnapshot
    ) throws IOException {
        // TODO (andponlin) Once the necessary version of the HaikuDepot desktop application is sufficiently
        //  distributed and people are no longer using the older versions then remove this logic.

        // TODO (andponlin) Could use information about the report to establish how long the report
        //  still has to run and use that timing to be more accurate for the client.

        LOGGER.info(
                "data not ready, client unable to retry --> wait for {}s to complete",
                JOB_COMPLETE_WAIT_TIME_SECONDS);

        if (!jobService.awaitJobFinishedUninterruptibly(
                jobSnapshot.getGuid(),
                JOB_COMPLETE_WAIT_TIME_SECONDS * 1000)) {
            LOGGER.warn(
                    "job [{}] did not finish in time --> return [{}]",
                    jobSnapshot.getGuid(),
                    HttpStatus.SERVICE_UNAVAILABLE);
            respondJobDataNotReady(response);
            return;
        }

        Optional<? extends JobSnapshot> refoundJobSnapshot = jobService.tryGetJob(jobSnapshot.getGuid());

        if (refoundJobSnapshot.isEmpty()) {
            LOGGER.error("the job [{}] was not found after waiting for it to complete", jobSnapshot.getGuid());
            response.setStatus(HttpStatus.NOT_FOUND.value());
            return;
        }

        jobSnapshot = refoundJobSnapshot.get();

        switch (jobSnapshot.getStatus()) {
            case FINISHED:
                LOGGER.info("data became ready --> redirect to data");
                respondRedirectToJobData(response, jobSnapshot);
                break;
            case QUEUED, STARTED:
                LOGGER.warn(
                        "job [{}] did not finish in time --> return [{}]",
                        jobSnapshot.getGuid(),
                        HttpStatus.SERVICE_UNAVAILABLE);
                respondJobDataNotReady(response);
                break;
            default:
                throw new IOException(
                        "job [%s] has status [%s]; error returned to client".formatted(
                                jobSnapshot.getGuid(),
                                jobSnapshot.getStatus()));
        }
    }

    private static void respondJobDataNotReady(HttpServletResponse response) {
        response.setHeader(HttpHeaders.RETRY_AFTER, Integer.toString(DELAY_JOB_DATA_NOT_READY_SECONDS));
        response.setStatus(HttpStatus.SERVICE_UNAVAILABLE.value());
    }

    private static void respondRedirectToJobData(
            HttpServletResponse response,
            JobSnapshot jobSnapshot
    ) throws IOException {
        Preconditions.checkArgument(jobSnapshot.getStatus() == JobSnapshot.Status.FINISHED, "the job must be finished");
        Preconditions.checkArgument(!jobSnapshot.getGeneratedDataGuids().isEmpty(), "the job must have generated data");

        String lastModifiedValue = DateTimeFormatter.RFC_1123_DATE_TIME.format(
                ZonedDateTime.ofInstant(jobSnapshot.getStartTimestamp().toInstant(), ZoneOffset.UTC));
        String destinationLocationUrl = UriComponentsBuilder.newInstance()
                .pathSegment(WebConstants.PATH_COMPONENT_SECURED)
                .pathSegment(JobController.SEGMENT_JOBDATA)
                .pathSegment(jobSnapshot.getGeneratedDataGuids().iterator().next())
                .pathSegment(JobController.SEGMENT_DOWNLOAD)
                .toUriString();

        response.addHeader(HttpHeaders.LAST_MODIFIED, lastModifiedValue);
        response.sendRedirect(destinationLocationUrl);
    }

    /**
     * @return {@code true} if the supplied Job is newer than the header timestamp. If there's no header then it will
     * return true.
     */

    private static boolean isJobDataAfterModifiedSinceHeader(
            JobSnapshot jobSnapshot,
            String ifModifiedSinceHeader) {
        Preconditions.checkNotNull(jobSnapshot);

        if (null != jobSnapshot.getDataTimestamp() && StringUtils.isNotBlank(ifModifiedSinceHeader)) {
            Instant requestModifyTimestamp = DateTimeHelper.secondAccuracyInstant(
                    Instant.from(DateTimeFormatter.RFC_1123_DATE_TIME.parse(ifModifiedSinceHeader)));

            Instant jobStartTimestamp = DateTimeHelper.secondAccuracyInstant(jobSnapshot.getDataTimestamp().toInstant());
            return jobStartTimestamp.isAfter(requestModifyTimestamp);
        }

        return true;
    }

    /**
     * @return {@code true} if the user agent is able to retry if it receives a 503 response. If it is not HaikuDepot
     * then return {@link true}.
     */

    private static boolean isUserAgentAbleToRetryOn503(String userAgent) {
        IntArrayVersionComparator versionComparator = new IntArrayVersionComparator();
        Optional<int[]> desktopVersions = DesktopApplicationHelper.tryDeriveVersionFromUserAgent(userAgent);
        return desktopVersions
                .map(v -> versionComparator.compare(v, VERSION_HD_503_RETRY) >= 0)
                .orElse(true);
    }

}
