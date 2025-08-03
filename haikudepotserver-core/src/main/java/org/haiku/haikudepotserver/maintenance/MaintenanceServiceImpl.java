/*
 * Copyright 2024-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.maintenance;

import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.haiku.haikudepotserver.dataobjects.Repository;
import org.haiku.haikudepotserver.dataobjects.UserPasswordResetToken;
import org.haiku.haikudepotserver.job.model.JobGarbageCollectionJobSpecification;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.maintenance.model.MaintenanceService;
import org.haiku.haikudepotserver.passwordreset.model.PasswordResetMaintenanceJobSpecification;
import org.haiku.haikudepotserver.repository.model.AlertRepositoryAbsentUpdateJobSpecification;
import org.haiku.haikudepotserver.repository.model.RepositoryHpkrIngressJobSpecification;
import org.haiku.haikudepotserver.storage.model.DataStorageGarbageCollectionJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * <p>Note that the exact (second, minute) of the timing of these expressions
 * is slightly 'random' in order that they are less likely to collide with
 * any other timed tasks.</p>
 *
 * <p>In the deployed environment, these calls are invoked via the Spring
 * Actuator system. For example, a Kubernetes CronJob will invoke an
 * Actuator HTTP endpoint on the application server will ends up triggering
 * the hourly and daily maintenance tasks here.</p>
 */

@Service
public class MaintenanceServiceImpl implements MaintenanceService {

    protected static Logger LOGGER = LoggerFactory.getLogger(MaintenanceServiceImpl.class);

    private final ServerRuntime serverRuntime;
    private final JobService jobService;

    public MaintenanceServiceImpl(
            ServerRuntime serverRuntime,
            JobService jobService) {
        this.serverRuntime = serverRuntime;
        this.jobService = jobService;
    }

    @Override
    public void daily() {
        // go through all the repositories and fetch them.  This is essentially a mop-up
        // task for those repositories that are unable to trigger a refresh.

        {
            ObjectContext context = serverRuntime.newContext();

            for(Repository repository : Repository.getAllActive(context)) {
                jobService.submit(
                        new RepositoryHpkrIngressJobSpecification(repository.getCode()),
                        JobSnapshot.COALESCE_STATUSES_QUEUED);
            }
        }

        // see if there appear to have been problems with importing HPKR files.

        jobService.submit(
                new AlertRepositoryAbsentUpdateJobSpecification(),
                JobSnapshot.COALESCE_STATUSES_QUEUED);

        LOGGER.info("did trigger daily maintenance");
    }

    @Override
    public void hourly() {
        // remove any jobs which are too old and are no longer required.

        {
            JobGarbageCollectionJobSpecification specification = new JobGarbageCollectionJobSpecification();
            jobService.submit(specification, JobSnapshot.COALESCE_STATUSES_QUEUED);
        }

        // remove any expired password reset tokens.

        {
            if (UserPasswordResetToken.hasAny(serverRuntime.newContext())) {
                jobService.submit(
                        new PasswordResetMaintenanceJobSpecification(),
                        JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED);
            }
            else {
                LOGGER.debug("did not submit task for password reset maintenance as there are no tokens stored");
            }
        }

        // garbage collect any data storage which is no longer in use.

        {
            DataStorageGarbageCollectionJobSpecification specification = new DataStorageGarbageCollectionJobSpecification();
            specification.setOlderThanMillis(Duration.ofHours(1).toMillis());
            jobService.submit(specification, JobSnapshot.COALESCE_STATUSES_QUEUED_STARTED);
        }

        LOGGER.info("did trigger hourly maintenance");
    }
}
