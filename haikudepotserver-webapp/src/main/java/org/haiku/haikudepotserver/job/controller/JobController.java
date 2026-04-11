/*
 * Copyright 2018-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.controller;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.ByteSource;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import jakarta.mail.internet.MimeUtility;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.*;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.haiku.haikudepotserver.support.web.WebConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.PermissionEvaluator;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    public final static String SEGMENT_JOBDATA = "jobdata";

    public final static String SEGMENT_DOWNLOAD = "download";

    private final static long MAX_SUPPLY_DATA_LENGTH = 1024 * 1024; // 1MB

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
            @RequestHeader(value = HttpHeaders.CONTENT_ENCODING, required = false) String contentEncoding,
            @RequestParam(value = KEY_USECODE, required = false, defaultValue = "supplied") String useCode)
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
                JobDataEncoding.getByHeaderValue(contentEncoding),
                new ByteSource() {
                    @Override
                    public InputStream openStream() throws IOException {
                        return request.getInputStream();
                    }
                }
        );

        response.setStatus(HttpServletResponse.SC_OK);
        response.setHeader(HEADER_DATAGUID, MimeUtility.encodeText(data.getGuid()));
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

            tryObtainAuthenticatedUser(context).orElseThrow(() -> {
                LOGGER.warn("attempt to obtain job data [{}] with no authenticated user", guid);
                return new JobDataAuthorizationFailure();
            });

            User ownerUser= User.tryGetByNickname(context, job.getOwnerUserNickname()).orElseThrow(() -> {
                LOGGER.warn("owner of job [{}] does not seem to exist", job.getOwnerUserNickname());
                return new JobDataAuthorizationFailure();
            });

            if (!permissionEvaluator.hasPermission(
                    SecurityContextHolder.getContext().getAuthentication(),
                    ownerUser,
                    Permission.USER_VIEWJOBS)) {
                LOGGER.warn("attempt to access jobs view for; {}", job);
                throw new JobDataAuthorizationFailure();
            }
        } else {
            LOGGER.debug("access to job [{}] allowed for unauthenticated access", job);
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
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION,
                MimeUtility.encodeText("attachment; filename="+ jobService.deriveDataFilename(guid)));
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        OutputStream outputStream = response.getOutputStream();
        jobDataWithByteSink.getByteSource().copyTo(outputStream);

        LOGGER.info("did start async stream job data; {}", guid);
    }

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="access to job data denied")
    private static class JobDataAuthorizationFailure extends RuntimeException {}

}
