/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.job;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobGarbageCollectionJobSpecification;
import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * <p>This job will delete any Jobs which have expired.</p>
 */

@Component
public class JobGarbageCollectionJobRunner extends AbstractJobRunner<JobGarbageCollectionJobSpecification> {

    @Override
    public Class<JobGarbageCollectionJobSpecification> getSupportedSpecificationClass() {
        return JobGarbageCollectionJobSpecification.class;
    }

    @Override
    public void run(JobService jobService, JobGarbageCollectionJobSpecification specification) throws IOException, JobRunnerException {
        Preconditions.checkNotNull(specification);
        Preconditions.checkNotNull(jobService);
        jobService.clearExpiredJobs();
    }

}
