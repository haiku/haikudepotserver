/*
 * Copyright 2014-2016, Andrew Lindesay
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
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobData;
import org.haiku.haikudepotserver.job.model.JobDataWithByteSource;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.security.AuthenticationFilter;
import org.haiku.haikudepotserver.security.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;

/**
 * <p>The job controller allows for upload and download of binary data related to jobs; for example, there are
 * various "spreadsheet" jobs that produce reports.  In such a case, the user may want to obtain the data for
 * such a report later; this controller will be able to provide that data.</p>
 */

@Controller
@RequestMapping(AuthenticationFilter.PREFIX_PATH_SECURED)
public class JobController extends AbstractController {

    private final static long MAX_SUPPLY_DATA_LENGTH = 1 * 1024 * 1024; // 1MB

    private final static String HEADER_DATAGUID = "X-HaikuDepotServer-DataGuid";

    protected static Logger LOGGER = LoggerFactory.getLogger(JobController.class);

    private final static String KEY_GUID = "guid";

    private final static String KEY_USECODE = "usecode";

    @Resource
    private JobService jobService;

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    /**
     * <p>This URL can be used to supply data that can be used with a job to be run as an input to the
     * job.  A GUID is returned in the header {@link #HEADER_DATAGUID} that can be later used to refer
     * to this uploaded data.</p>
     */

    @RequestMapping(value = "/jobdata", method = RequestMethod.POST)
    @ResponseBody
    public void supplyData(
            final HttpServletRequest request,
            final HttpServletResponse response,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType,
            @RequestParam(value = KEY_USECODE, required = false) String useCode)
    throws IOException {

        Preconditions.checkArgument(null!=request, "the request must be provided");

        int length = request.getContentLength();

        if(-1 != length && length > MAX_SUPPLY_DATA_LENGTH) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        }

        ObjectContext context = serverRuntime.getContext();

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

    @RequestMapping(value = "/jobdata/{" + KEY_GUID + "}/download", method = RequestMethod.GET)
    public void downloadGeneratedData(
            HttpServletResponse response,
            @PathVariable(value = KEY_GUID) String guid)
    throws IOException {

        ObjectContext context = serverRuntime.getContext();

        // lots and lots of checking that the user who is authenticated can actually view
        // the data.

        User user = tryObtainAuthenticatedUser(context).orElseThrow(() -> {
                    LOGGER.warn("attempt to obtain job data {} with no authenticated user", guid);
                    return new JobDataAuthorizationFailure();
                });

        JobSnapshot job = jobService.tryGetJobForData(guid).orElseThrow(() -> {
            LOGGER.warn("attempt to access job data {} for which no job exists", guid);
            return new JobDataAuthorizationFailure();
        });

        if(Strings.isNullOrEmpty(job.getOwnerUserNickname())) {
            if (!authorizationService.check(context, user, null, Permission.JOBS_VIEW)) {
                LOGGER.warn("attempt to access job data {} but was not authorized", guid);
                throw new JobDataAuthorizationFailure();
            }
        }
        else {
            User ownerUser= User.getByNickname(context, job.getOwnerUserNickname()).orElseThrow(() -> {
                LOGGER.warn("owner of job does not seem to exist; {}", job.getOwnerUserNickname());
                return new JobDataAuthorizationFailure();
            });

            if (!authorizationService.check(context, user, ownerUser, Permission.USER_VIEWJOBS)) {
                LOGGER.warn("attempt to access jobs view for; {}", job.toString());
                throw new JobDataAuthorizationFailure();
            }
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

        jobDataWithByteSink.getByteSource().copyTo(response.getOutputStream());

        LOGGER.info("did stream job data; {}", guid);

    }

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="access to job data denied")
    private class JobDataAuthorizationFailure extends RuntimeException {}

}
