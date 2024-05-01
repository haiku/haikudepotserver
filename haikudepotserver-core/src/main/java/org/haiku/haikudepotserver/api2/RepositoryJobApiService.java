/*
 * Copyright 2024, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.api2;

import com.google.common.base.Preconditions;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.api2.model.QueueRepositoryDumpExportJobResult;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.dataobjects.auto._User;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.repository.model.RepositoryDumpExportJobSpecification;
import org.haiku.haikudepotserver.security.PermissionEvaluator;
import org.haiku.haikudepotserver.security.model.Permission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component("repositoryJobApiServiceV2")
public class RepositoryJobApiService extends AbstractApiService {

    protected final static Logger LOGGER = LoggerFactory.getLogger(RepositoryJobApiService.class);

    private final ServerRuntime serverRuntime;

    private final PermissionEvaluator permissionEvaluator;

    private final JobService jobService;

    public RepositoryJobApiService(
            ServerRuntime serverRuntime,
            PermissionEvaluator permissionEvaluator,
            JobService jobService) {
        this.serverRuntime = Preconditions.checkNotNull(serverRuntime);
        this.permissionEvaluator = Preconditions.checkNotNull(permissionEvaluator);
        this.jobService = Preconditions.checkNotNull(jobService);
    }

    public QueueRepositoryDumpExportJobResult queueRepositoryDumpExportJob() {

        final ObjectContext context = serverRuntime.newContext();

        Optional<User> user = tryObtainAuthenticatedUser(context);

        if(!permissionEvaluator.hasPermission(
                SecurityContextHolder.getContext().getAuthentication(),
                null,
                Permission.REPOSITORY_LIST)) {
            throw new AccessDeniedException("attempt to run a repository dump");
        }

        // setup and go

        RepositoryDumpExportJobSpecification spec = new RepositoryDumpExportJobSpecification();
        spec.setOwnerUserNickname(user.map(_User::getNickname).orElse(null));

        return new QueueRepositoryDumpExportJobResult()
                .guid(jobService.submit(spec, JobSnapshot.COALESCE_STATUSES_NONE));
    }

}