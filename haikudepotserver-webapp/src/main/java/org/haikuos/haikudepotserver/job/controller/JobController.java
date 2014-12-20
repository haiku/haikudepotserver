/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job.controller;

import com.google.common.base.Optional;
import com.google.common.base.Strings;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.job.JobOrchestrationService;
import org.haikuos.haikudepotserver.job.model.JobData;
import org.haikuos.haikudepotserver.job.model.JobDataWithByteSource;
import org.haikuos.haikudepotserver.job.model.JobSnapshot;
import org.haikuos.haikudepotserver.security.AuthorizationService;
import org.haikuos.haikudepotserver.security.model.Permission;
import org.haikuos.haikudepotserver.support.web.AbstractController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * <p>The job controller allows for upload and download of binary data related to jobs; for example, there are
 * various "spreadsheet" jobs that produce reports.  In such a case, the user may want to obtain the data for
 * such a report later; this controller will be able to provide that data.</p>
 */

@Controller
@RequestMapping("/secured")
public class JobController extends AbstractController {

    protected static Logger LOGGER = LoggerFactory.getLogger(JobController.class);

    public final static String KEY_GUID = "guid";

    @Resource
    private JobOrchestrationService jobOrchestrationService;

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    @RequestMapping(value = "/jobdata/{" + KEY_GUID + "}/download", method = RequestMethod.GET)
    public void data(
            HttpServletResponse response,
            @PathVariable(value = KEY_GUID) String guid)
    throws IOException{

        ObjectContext context = serverRuntime.getContext();

        // lots and lots of checking that the user who is authenticated can actually view
        // the data.

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!user.isPresent()) {
            LOGGER.warn("attempt to obtain job data {} with no authenticated user", guid);
            throw new JobDataAuthorizationFailure();
        }

        Optional<? extends JobSnapshot> jobOptional = jobOrchestrationService.tryGetJobForData(guid);

        if(!jobOptional.isPresent()) {
            LOGGER.warn("attempt to access job data {} for which no job exists", guid);
            throw new JobDataAuthorizationFailure();
        }

        JobSnapshot job = jobOptional.get();

        if(Strings.isNullOrEmpty(job.getOwnerUserNickname())) {
            if (!authorizationService.check(context, user.get(), null, Permission.JOBS_VIEW)) {
                LOGGER.warn("attempt to access job data {} but was not authorized", guid);
                throw new JobDataAuthorizationFailure();
            }
        }
        else {
            Optional<User> ownerUserOptional = User.getByNickname(context, job.getOwnerUserNickname());

            if(!ownerUserOptional.isPresent()) {
                LOGGER.warn("owner of job does not seem to exist; {}", job.getOwnerUserNickname());
                throw new JobDataAuthorizationFailure();
            }

            if (!authorizationService.check(context, user.get(), ownerUserOptional.get(), Permission.USER_VIEWJOBS)) {
                LOGGER.warn("attempt to access jobs view for; {}", job.toString());
                throw new JobDataAuthorizationFailure();
            }
        }

        Optional<JobDataWithByteSource> jobDataWithByteSinkOptional = jobOrchestrationService.tryObtainData(guid);

        if(!jobDataWithByteSinkOptional.isPresent()) {
            LOGGER.warn("requested job data {} not found", guid);
            throw new JobDataAuthorizationFailure();
        }

        // finally access has been checked and the logic can move onto actual
        // delivery of the material.

        JobData jobData = jobDataWithByteSinkOptional.get().getJobData();

        if(!Strings.isNullOrEmpty(jobData.getMediaTypeCode())) {
            response.setContentType(jobData.getMediaTypeCode());
        }
        else {
            response.setContentType(MediaType.OCTET_STREAM.toString());
        }

        response.setContentType(MediaType.CSV_UTF_8.toString());
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename="+jobOrchestrationService.deriveDataFilename(guid));
        response.setDateHeader(HttpHeaders.EXPIRES, 0);
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");

        jobDataWithByteSinkOptional.get().getByteSource().copyTo(response.getOutputStream());

        LOGGER.info("did stream job data; {}", guid);

    }

    @ResponseStatus(value= HttpStatus.UNAUTHORIZED, reason="access to job data denied")
    public class JobDataAuthorizationFailure extends RuntimeException {}

}
