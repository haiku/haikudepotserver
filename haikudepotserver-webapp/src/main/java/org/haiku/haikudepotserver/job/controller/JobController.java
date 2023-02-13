/*
 * Copyright 2018-2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.haiku.haikudepotserver.support.web.JobDataWriteListener;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.AsyncContext;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/**
 * <p>The job controller allows for upload and download of binary data related to jobs; for example, there are
 * various "spreadsheet" jobs that produce reports.  In such a case, the user may want to obtain the data for
 * such a report later; this controller will be able to provide that data.</p>
 */

@Controller
@RequestMapping("/" + WebConstants.PATH_COMPONENT_SECURED)
public class JobController extends AbstractController {

    protected final static Logger LOGGER = LoggerFactory.getLogger(JobController.class);

    private final static Pattern PATTERN_GUID = Pattern.compile("^[A-Za-z0-9_-]+$");

    private final static String SEGMENT_JOBDATA = "jobdata";

    private final static String SEGMENT_DOWNLOAD = "download";

    private final static long MAX_SUPPLY_DATA_LENGTH = 1024 * 1024; // 1MB

    private final static long TIMEOUT_DOWNLOAD_MILLIS = TimeUnit.MINUTES.toMillis(2);

    private final static String HEADER_DATAGUID = "X-HaikuDepotServer-DataGuid";

    private final static String KEY_GUID = "guid";

    private final static String KEY_USECODE = "usecode";

    private final JobService jobService;
    private final ServerRuntime serverRuntime;
    private final PermissionEvaluator permissionEvaluator;

    public JobController(
            ServerRuntime serverRuntime,
            JobService jobService,
            PermissionEvaluator permissionEvaluator) {
        this.jobService = Preconditions.checkNotNull(jobService);
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
    }

    /**
     * <p>This is helper-code that can be used to check to see if the data is stale and
     * will then enqueue the job, run it and then redirect the user to the data
     * download.</p>
     * @param response is the HTTP response to send the redirect to.
     * @param ifModifiedSinceHeader is the inbound header from the client.
     * @param lastModifyTimestamp is the actual last modified date for the data.
     * @param jobSpecification is the job that would be run if the data is newer than in the
     *                         inbound header.
     */

    public static void handleRedirectToJobData(
            HttpServletResponse response,
            JobService jobService,
            String ifModifiedSinceHeader,
            Date lastModifyTimestamp,
            JobSpecification jobSpecification) throws IOException {

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

    private static JobSnapshot getJobSnapshotStartedAfter(
            JobService jobService,
            Date lastModifyTimestamp,
            JobSpecification jobSpecification) {
        for (int i = 0; i < 3; i++) {
            String jobGuid = jobService.immediate(jobSpecification, true);
            JobSnapshot jobSnapshot = jobService.tryGetJob(jobGuid)
                    .orElseThrow(() -> new IllegalStateException("unable to obtain the job snapshot having run it immediate prior."));

            if (jobSnapshot.getStartTimestamp().getTime() >= lastModifyTimestamp.getTime()) {
                return jobSnapshot;
            }

            jobService.removeJob(jobGuid); // remove the stale one.
        }

        throw new IllegalStateException("unable to find a job snapshot started after [" + lastModifyTimestamp + "]");
    }

    /**
     * <p>This URL can be used to supply data that can be used with a job to be run as an input to the
     * job.  A GUID is returned in the header {@link #HEADER_DATAGUID} that can be later used to refer
     * to this uploaded data.</p>
     */

    @RequestMapping(value = "/" + SEGMENT_JOBDATA, method = RequestMethod.POST)
    @ResponseBody
    public void supplyData(
            final HttpServletRequest request,
            final HttpServletResponse response,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestParam(value = KEY_USECODE, required = false) String useCode)
    throws IOException {

        Preconditions.checkArgument(null!=request, "the request must be provided");

        int length = request.getContentLength();

        if(-1 == length || length > MAX_SUPPLY_DATA_LENGTH) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        ObjectContext context = serverRuntime.newContext();

        tryObtainAuthenticatedUser(context).orElseThrow(() -> {
            LOGGER.warn("attempt to supply job data with no authenticated user");
            return new JobDataAuthorizationFailure();
        });

        JobData data = jobService.storeSuppliedData(
                useCode,
                !Strings.isNullOrEmpty(contentType) ? contentType : MediaType.OCTET_STREAM.toString(),
                new ByteSource() {
                    @Override
                    public InputStream openStream() throws IOException {
                        return request.getInputStream();
                    }
                }
        );

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HEADER_DATAGUID, data.getGuid());
    }

    /**
     * <p>This URL can be used to download job data that has resulted from a job being run.</p>
     */

    @RequestMapping(value = "/" + SEGMENT_JOBDATA + "/{" + KEY_GUID + "}/" + SEGMENT_DOWNLOAD, method = RequestMethod.GET)
    public void downloadGeneratedData(
            HttpServletRequest request,
            HttpServletResponse response,
            @PathVariable(value = KEY_GUID) String guid)
    throws IOException {

        Preconditions.checkArgument(PATTERN_GUID.matcher(guid).matches(), "the supplied guid does not match the required pattern");

        ObjectContext context = serverRuntime.newContext();

        JobSnapshot job = jobService.tryGetJobForData(guid).orElseThrow(() -> {
            LOGGER.warn("attempt to access job data {} for which no job exists", guid);
            return new JobDataAuthorizationFailure();
        });

        // If there is no user who is assigned to the job then the job is for nobody in particular and is thereby
        // secured by the GUID of the job's data; if you know the GUID then you can have the data.

        if(!Strings.isNullOrEmpty(job.getOwnerUserNickname())) {

            User user = tryObtainAuthenticatedUser(context).orElseThrow(() -> {
                LOGGER.warn("attempt to obtain job data {} with no authenticated user", guid);
                return new JobDataAuthorizationFailure();
            });

            User ownerUser= User.tryGetByNickname(context, job.getOwnerUserNickname()).orElseThrow(() -> {
                LOGGER.warn("owner of job does not seem to exist; {}", job.getOwnerUserNickname());
                return new JobDataAuthorizationFailure();
            });

            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    ownerUser,
                    Permission.USER_VIEWJOBS)) {
                LOGGER.warn("attempt to access jobs view for; {}", job.toString());
                throw new JobDataAuthorizationFailure();
            }
        } else {
            LOGGER.debug("access to job [{}] allowed for unauthenticated access", job.toString());
        }

        JobDataWithByteSource jobDataWithByteSink = jobService.tryObtainData(guid).orElseThrow(() -> {
            LOGGER.warn("requested job data {} not found", guid);
            return new JobDataAuthorizationFailure();
        });

        // finally access has been checked and the logic can move onto actual
        // delivery of the material.

        JobData jobData = jobDataWithByteSink.getJobData();

        if(!Strings.isNullOrEmpty(jobData.getMediaTypeCode())) {
            response.setContentType(jobData.getMediaTypeCode());
        }
        else {
            response.setContentType(MediaType.OCTET_STREAM.toString());
        }

        response.setContentType(MediaType.CSV_UTF_8.toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+ jobService.deriveDataFilename(guid));
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        // now switch to async for the delivery of the data.

        AsyncContext async = request.startAsync();
        async.setTimeout(TIMEOUT_DOWNLOAD_MILLIS);
        ServletOutputStream outputStream = response.getOutputStream();
        outputStream.setWriteListener(new JobDataWriteListener(
                guid, jobService, async, outputStream));

        LOGGER.info("did start async stream job data; {}", guid);

    }

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="access to job data denied")
    private static class JobDataAuthorizationFailure extends RuntimeException {}

}
