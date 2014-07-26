/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.security.controller;

import com.google.common.base.Preconditions;
import com.google.common.net.HttpHeaders;
import com.google.common.net.MediaType;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.security.AbstractUserAuthenticationAware;
import org.haikuos.haikudepotserver.security.AuthorizationPkgRuleOrchestrationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@Controller
@RequestMapping("/secured/authorization/authorizationpkgrule")
public class AuthorizationPkgRuleController extends AbstractUserAuthenticationAware {

    protected static Logger logger = LoggerFactory.getLogger(AuthorizationPkgRuleController.class);

    @Resource
    ServerRuntime serverRuntime;

    @Resource
    AuthorizationPkgRuleOrchestrationService authorizationPkgRuleOrchestrationService;

    /**
     * <p>This method will produce a CSV file containing all of the rules.</p>
     */

    @RequestMapping(value = "/download.csv", method = RequestMethod.GET)
    public void download(
            HttpServletResponse httpResponse
    ) {

        Preconditions.checkNotNull(httpResponse);

        final ObjectContext context = serverRuntime.getContext();
        User user = obtainAuthenticatedUser(context);

        if(null==user || !user.getIsRoot()) {
            httpResponse.setStatus(HttpServletResponse.SC_FORBIDDEN);
        }

        httpResponse.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.CSV_UTF_8.toString());
        httpResponse.setHeader(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=authorizationpkgrule.csv");

        try {
            authorizationPkgRuleOrchestrationService.generateCsv(context, httpResponse.getOutputStream());
        }
        catch(IOException ioe) {
            throw new RuntimeException("unable to produce the authorizationpkgrule csv download", ioe);
        }

        logger.info("did generate the authorizationpkgrule csv download");

    }

}
