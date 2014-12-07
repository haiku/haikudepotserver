/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support.job;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.support.job.model.Job;
import org.haikuos.haikudepotserver.support.job.model.JobSpecification;
import org.haikuos.haikudepotserver.support.job.model.TestJobSpecificationImpl;
import org.joda.time.DateTime;

import javax.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>This implementation of {@link org.haikuos.haikudepotserver.support.job.JobOrchestrationService} has some
 * jobs in "suspended" state so that tests of the {@link org.haikuos.haikudepotserver.api1.JobApi} are able to
 * query that known state.</p>
 */

public class TestJobOrchestrationServiceImpl implements JobOrchestrationService {

    public final static DateTime DT_1976_JAN = new DateTime(1976,1,1,0,0);
    public final static DateTime DT_1976_FEB = new DateTime(1976,2,1,0,0);
    public final static DateTime DT_1976_MAR = new DateTime(1976,3,1,0,0);
    public final static DateTime DT_1976_APR = new DateTime(1976,4,1,0,0);
    public final static DateTime DT_1976_JUN = new DateTime(1976,6,1,0,0);
    public final static DateTime DT_1976_JUL = new DateTime(1976,6,1,0,0);

    private List<Job> jobs;

    public TestJobOrchestrationServiceImpl() {
        super();
    }

    @PostConstruct
    public void init() {
        jobs = Lists.newArrayList();

        {
            Job j = new Job();
            j.setQueuedTimestamp(DT_1976_JAN.toDate());
            j.setJobSpecification(new TestJobSpecificationImpl("queued"));
            jobs.add(j);
        }

        {
            Job j = new Job();
            j.setQueuedTimestamp(DT_1976_FEB.toDate());
            j.setStartTimestamp(DT_1976_MAR.toDate());
            j.setJobSpecification(new TestJobSpecificationImpl("started"));
            jobs.add(j);
        }

        {
            Job j = new Job();
            j.setOwnerUserNickname("testuser");
            j.setQueuedTimestamp(DT_1976_APR.toDate());
            j.setStartTimestamp(DT_1976_JUN.toDate());
            j.setFinishTimestamp(DT_1976_JUL.toDate());
            j.setJobSpecification(new TestJobSpecificationImpl("finished"));
            jobs.add(j);
        }

    }

    @Override
    public Optional<String> submit(JobSpecification specification, CoalesceMode coalesceMode) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setJobFailTimestamp(String guid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setJobCancelTimestamp(String guid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<Job> tryGetJob(String guid) {
        return null;
    }

    @Override
    public void setJobProgressPercent(String guid, Integer progressPercent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearExpiredJobs() {
        throw new UnsupportedOperationException();
    }

    private boolean matches(Job job, User user, Set<Job.Status> statuses) {
        if(null!=user) {
            if(null==job.getOwnerUserNickname()) {
                return false;
            }

            if(!job.getOwnerUserNickname().equals(user.getNickname())) {
                return false;
            }
        }

        if(null!=statuses) {
            if(!statuses.contains(job.getStatus())) {
                return false;
            }
        }

        return true;
    }

    private List<Job> filteredJobs(final User user, final Set<Job.Status> statuses) {
        return Lists.newArrayList(
                Iterables.filter(
                        jobs,
                        new Predicate<Job>() {
                            @Override
                            public boolean apply(Job input) {
                                return matches(input, user, statuses);
                            }
                        }
                )
        );
    }

    @Override
    public List<Job> findJobs(User user, Set<Job.Status> statuses, int offset, int limit) {
        List<Job> result = filteredJobs(user, statuses);

        if(offset >= result.size()) {
            return Collections.emptyList();
        }

        Collections.sort(result);

        if(offset + limit > result.size()) {
            limit = result.size() - offset;
        }

        return result.subList(offset, offset+limit);
    }

    @Override
    public int totalJobs(User user, Set<Job.Status> statuses) {
        return filteredJobs(user, statuses).size();
    }

}
