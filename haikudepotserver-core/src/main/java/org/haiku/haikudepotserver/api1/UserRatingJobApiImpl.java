/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.api1;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.googlecode.jsonrpc4j.spring.AutoJsonRpcServiceImpl;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api1.model.userrating.job.QueueUserRatingSpreadsheetJobRequest;
import org.haiku.haikudepotserver.api1.model.userrating.job.QueueUserRatingSpreadsheetJobResult;
import org.haiku.haikudepotserver.api1.support.AuthorizationFailureException;
import org.haiku.haikudepotserver.api1.support.ObjectNotFoundException;
import org.haiku.haikudepotserver.dataobjects.Pkg;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.security.model.AuthorizationService;
import org.haiku.haikudepotserver.security.model.Permission;
import org.haiku.haikudepotserver.userrating.model.UserRatingSpreadsheetJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@AutoJsonRpcServiceImpl
public class UserRatingJobApiImpl extends AbstractApiImpl implements UserRatingJobApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingJobApiImpl.class);

    private ServerRuntime serverRuntime;
    private AuthorizationService authorizationService;
    private JobService jobService;

    public UserRatingJobApiImpl(
            ServerRuntime serverRuntime,
            AuthorizationService authorizationService,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.authorizationService = Preconditions.checkNotNull(authorizationService);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    @Override
    public QueueUserRatingSpreadsheetJobResult queueUserRatingSpreadsheetJob(QueueUserRatingSpreadsheetJobRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request);
        Preconditions.checkArgument(Strings.isNullOrEmpty(request.pkgName) || Strings.isNullOrEmpty(request.userNickname),"the user nickname or pkg name can be supplied, but not both");

        final ObjectContext context = serverRuntime.newContext();

        User user = obtainAuthenticatedUser(context);
        UserRatingSpreadsheetJobSpecification spec = new UserRatingSpreadsheetJobSpecification();

        if(!Strings.isNullOrEmpty(request.repositoryCode)) {
            spec.setRepositoryCode(getRepository(context, request.repositoryCode).getCode());
        }

        if(!Strings.isNullOrEmpty(request.userNickname)) {
            Optional<User> requestUserOptional = User.tryGetByNickname(context, request.userNickname);

            if(!requestUserOptional.isPresent()) {
                LOGGER.warn("attempt to produce user rating report for user {}, but that user does not exist -- not allowed", request.userNickname);
                throw new AuthorizationFailureException();
            }

            if(!authorizationService.check(
                    context,
                    user,
                    requestUserOptional.get(),
                    Permission.BULK_USERRATINGSPREADSHEETREPORT_USER)) {
                LOGGER.warn("attempt to access a user rating report for user {}, but this was disallowed", request.userNickname);
                throw new AuthorizationFailureException();
            }

            spec.setUserNickname(request.userNickname);
        }
        else {

            if (!Strings.isNullOrEmpty(request.pkgName)) {
                Optional<Pkg> requestPkgOptional = Pkg.tryGetByName(context, request.pkgName);

                if (!requestPkgOptional.isPresent()) {
                    LOGGER.warn("attempt to produce user rating report for pkg {}, but that pkg does not exist -- not allowed", request.pkgName);
                    throw new AuthorizationFailureException();
                }

                if (!authorizationService.check(
                        context,
                        user,
                        requestPkgOptional.get(),
                        Permission.BULK_USERRATINGSPREADSHEETREPORT_PKG)) {
                    LOGGER.warn("attempt to access a user rating report for pkg {}, but this was disallowed", request.pkgName);
                    throw new AuthorizationFailureException();
                }

                spec.setPkgName(request.pkgName);
            }
            else {
                if (!authorizationService.check(
                        context,
                        user,
                        null,
                        Permission.BULK_USERRATINGSPREADSHEETREPORT_ALL)) {
                    LOGGER.warn("attempt to access a user rating report, but was unauthorized");
                    throw new AuthorizationFailureException();
                }
            }
        }

        spec.setOwnerUserNickname(user.getNickname());

        return new QueueUserRatingSpreadsheetJobResult(
                jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED));

    }


}
