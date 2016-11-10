/*
 * Copyright 2016, Andrew Lindesay
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

import javax.annotation.Resource;
import java.util.Optional;

@AutoJsonRpcServiceImpl
public class UserRatingJobApiImpl extends AbstractApiImpl implements UserRatingJobApi {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingJobApiImpl.class);

    @Resource
    private ServerRuntime serverRuntime;

    @Resource
    private AuthorizationService authorizationService;

    @Resource
    private JobService jobService;

    @Override
    public QueueUserRatingSpreadsheetJobResult queueUserRatingSpreadsheetJob(QueueUserRatingSpreadsheetJobRequest request) throws ObjectNotFoundException {
        Preconditions.checkArgument(null!=request);
        Preconditions.checkArgument(Strings.isNullOrEmpty(request.pkgName) || Strings.isNullOrEmpty(request.userNickname),"the user nickname or pkg name can be supplied, but not both");

        final ObjectContext context = serverRuntime.getContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);
        UserRatingSpreadsheetJobSpecification spec = new UserRatingSpreadsheetJobSpecification();

        if(!Strings.isNullOrEmpty(request.repositoryCode)) {
            spec.setRepositoryCode(getRepository(context, request.repositoryCode).getCode());
        }

        if(!Strings.isNullOrEmpty(request.userNickname)) {
            Optional<User> requestUserOptional = User.getByNickname(context, request.userNickname);

            if(!requestUserOptional.isPresent()) {
                LOGGER.warn("attempt to produce user rating report for user {}, but that user does not exist -- not allowed", request.userNickname);
                throw new AuthorizationFailureException();
            }

            if(!authorizationService.check(
                    context,
                    user.orElse(null),
                    requestUserOptional.get(),
                    Permission.BULK_USERRATINGSPREADSHEETREPORT_USER)) {
                LOGGER.warn("attempt to access a user rating report for user {}, but this was disallowed", request.userNickname);
                throw new AuthorizationFailureException();
            }

            spec.setUserNickname(request.userNickname);
        }
        else {

            if (!Strings.isNullOrEmpty(request.pkgName)) {
                Optional<Pkg> requestPkgOptional = Pkg.getByName(context, request.pkgName);

                if (!requestPkgOptional.isPresent()) {
                    LOGGER.warn("attempt to produce user rating report for pkg {}, but that pkg does not exist -- not allowed", request.pkgName);
                    throw new AuthorizationFailureException();
                }

                if (!authorizationService.check(
                        context,
                        user.orElse(null),
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
                        user.orElse(null),
                        null,
                        Permission.BULK_USERRATINGSPREADSHEETREPORT_ALL)) {
                    LOGGER.warn("attempt to access a user rating report, but was unauthorized");
                    throw new AuthorizationFailureException();
                }
            }
        }

        spec.setOwnerUserNickname(user.get().getNickname());

        return new QueueUserRatingSpreadsheetJobResult(
                jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED));

    }


}
