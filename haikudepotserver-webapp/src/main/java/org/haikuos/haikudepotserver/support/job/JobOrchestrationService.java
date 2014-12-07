/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job;

import com.google.common.base.Optional;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.support.job.model.Job;
import org.haikuos.haikudepotserver.support.job.model.JobSpecification;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

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

    void setJobFailTimestamp(String guid);

    void setJobCancelTimestamp(String guid);

    /**
     * <P>This will return a clone of the {@link org.haikuos.haikudepotserver.support.job.model.Job}
     * as opposed to a 'working' object.</P>
     */

    Optional<Job> tryGetJob(String guid);

    void setJobProgressPercent(String guid, Integer progressPercent);

    void clearExpiredJobs();

    /**
     * <p>This method will return an ordered list of the
     * {@link org.haikuos.haikudepotserver.support.job.model.Job} that belong to the specified
     * user (or all users if this value is null).  It will return those
     * {@link org.haikuos.haikudepotserver.support.job.model.Job} objects that are from the
     * specified offset with a maximum of the specified limit.</p>
     * @param user only return {@link org.haikuos.haikudepotserver.support.job.model.Job} objects
     *             for this user.  If the user is null then return values for any user.
     * @param statuses only return {@link org.haikuos.haikudepotserver.support.job.model.Job}
     *                 objects that have the specified status.
     */

    List<Job> findJobs(User user, Set<Job.Status> statuses, int offset, int limit);

    /**
     * <p>This method returns the count of packages that could be returned from
     * {@link #findJobs(org.haikuos.haikudepotserver.dataobjects.User, java.util.Set, int, int)}
     * but without the offset and limits considered.</p>
     */

    int totalJobs(User user, Set<Job.Status> statuses);

}
