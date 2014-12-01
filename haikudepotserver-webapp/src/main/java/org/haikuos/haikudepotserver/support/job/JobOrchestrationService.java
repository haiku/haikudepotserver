/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job;

import com.google.common.base.Optional;
import org.haikuos.haikudepotserver.support.job.model.JobRunState;
import org.haikuos.haikudepotserver.support.job.model.JobSpecification;
import org.springframework.stereotype.Service;

/**
 * <p>An implementation of this type of service is able to take submitted job specifications, couple each to
 * a runner and will run the specification.  It coordinates the running of jobs.</p>
 */

@Service
public interface JobOrchestrationService {

    enum CoalesceMode {
        NONE,
        QUEUED,
        QUEUEDANDSTARTED
    }

    Optional<String> submit(JobSpecification specification, CoalesceMode coalesceMode);

    void setJobRunFailTimestamp(String guid);

    void setJobRunCancelTimestamp(String guid);

    /**
     * <P>This will return a clone of the {@link org.haikuos.haikudepotserver.support.job.model.JobRunState}
     * as opposed to a 'working' object.</P>
     */

    Optional<JobRunState> tryGetJobRunState(String guid);

    void setJobRunProgressPercent(String guid, Integer progressPercent);

    void clearExpiredJobs();

}
