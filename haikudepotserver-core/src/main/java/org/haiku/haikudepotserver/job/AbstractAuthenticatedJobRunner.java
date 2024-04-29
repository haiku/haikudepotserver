/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSpecification;
import org.haiku.haikudepotserver.security.UserAuthentication;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.io.IOException;
import java.util.Optional;

/**
 * <p>The idea here is that the user who owns the job will be set to be the authenticated user. If there is no
 * owner user then it will not set the authentication.</p>
 */

public abstract class AbstractAuthenticatedJobRunner<T extends JobSpecification> extends AbstractJobRunner<T> {

    protected final ServerRuntime serverRuntime;

    public AbstractAuthenticatedJobRunner(ServerRuntime serverRuntime) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
    }

    @Override
    public void run(JobService jobService, T specification)
            throws IOException, JobRunnerException {
        Optional<Authentication> authenticationOptional = tryDeriveAuthentication(specification.getOwnerUserNickname());
        Authentication authenticationPrior = SecurityContextHolder.getContext().getAuthentication();

        try {
            SecurityContextHolder.getContext().setAuthentication(authenticationOptional.orElse(null));
            runPossiblyAuthenticated(jobService, specification);
        }
        finally {
            SecurityContextHolder.getContext().setAuthentication(authenticationPrior);
        }
    }

    protected abstract void runPossiblyAuthenticated(JobService jobService, T specification)
            throws IOException, JobRunnerException;

    private Optional<Authentication> tryDeriveAuthentication(String ownerUserNickname) {
        ObjectContext context = serverRuntime.newContext();
        return Optional.ofNullable(ownerUserNickname)
                .map(StringUtils::trimToNull)
                .flatMap(n -> User.tryGetByNickname(context, n))
                .map(User::getObjectId)
                .map(oid -> {
                  Authentication authentication = new UserAuthentication(oid);
                  authentication.setAuthenticated(true);
                  return authentication;
                });
    }

}
