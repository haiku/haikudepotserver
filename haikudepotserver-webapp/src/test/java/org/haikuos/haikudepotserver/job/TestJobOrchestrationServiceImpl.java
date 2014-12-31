/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job;

import com.google.common.base.Optional;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.io.ByteSource;
import org.haikuos.haikudepotserver.dataobjects.User;
import org.haikuos.haikudepotserver.job.model.*;
import org.joda.time.DateTime;

import javax.annotation.PostConstruct;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * <p>This implementation of {@link org.haikuos.haikudepotserver.job.JobOrchestrationService} has some
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

    private Job queuedJob;
    private Job startedJob;
    private Job finishedJob;

    public TestJobOrchestrationServiceImpl() {
        super();
    }

    @PostConstruct
    public void init() {

        {
            queuedJob = new Job();
            queuedJob.setQueuedTimestamp(DT_1976_JAN.toDate());
            queuedJob.setJobSpecification(new TestJobSpecificationImpl(null,"queued"));
        }

        {
            startedJob = new Job();
            startedJob.setQueuedTimestamp(DT_1976_FEB.toDate());
            startedJob.setStartTimestamp(DT_1976_MAR.toDate());
            startedJob.setJobSpecification(new TestJobSpecificationImpl(null,"started"));
        }

        {
            finishedJob = new Job();
            finishedJob.setQueuedTimestamp(DT_1976_APR.toDate());
            finishedJob.setStartTimestamp(DT_1976_JUN.toDate());
            finishedJob.setFinishTimestamp(DT_1976_JUL.toDate());
            finishedJob.setJobSpecification(new TestJobSpecificationImpl("testuser", "finished"));
        }

    }

    @Override
    public Optional<String> submit(
            JobSpecification specification,
            CoalesceMode coalesceMode) {
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
    public Optional<? extends JobSnapshot> tryGetJob(String guid) {
        switch(guid) {
            case "queued" : return Optional.of(queuedJob);
            case "started" : return Optional.of(startedJob);
            case "finished" : return Optional.of(finishedJob);
        }

        return Optional.absent();
    }

    @Override
    public void setJobProgressPercent(String guid, Integer progressPercent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearExpiredJobs() {
        throw new UnsupportedOperationException();
    }

    private boolean matches(JobSnapshot job, User user, Set<JobSnapshot.Status> statuses) {
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

    private List<Job> filteredJobs(final User user, final Set<JobSnapshot.Status> statuses) {
        return Lists.newArrayList(
                Iterables.filter(
                        ImmutableList.of(
                                queuedJob,
                                startedJob,
                                finishedJob
                        ),
                        new Predicate<JobSnapshot>() {
                            @Override
                            public boolean apply(JobSnapshot input) {
                                return matches(input, user, statuses);
                            }
                        }
                )
        );
    }

    @Override
    public List<? extends JobSnapshot> findJobs(User user, Set<JobSnapshot.Status> statuses, int offset, int limit) {
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
    public int totalJobs(User user, Set<JobSnapshot.Status> statuses) {
        return filteredJobs(user, statuses).size();
    }

    @Override
    public JobDataWithByteSink storeGeneratedData(String jobGuid, String useCode, String mediaTypeCode) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public JobData storeSuppliedData(String useCode, String mediaTypeCode, ByteSource byteSource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String deriveDataFilename(String guid) {
        return "file.dat";
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid) {
        return Optional.absent();
    }

    @Override
    public Optional<JobData> tryGetData(String guid) {
        return Optional.absent();
    }

    @Override
    public Optional<JobDataWithByteSource> tryObtainData(String guid) throws IOException {
        return Optional.absent();
    }

    @Override
    public void awaitJobConcludedUninterruptibly(String guid, long timeout) {
        // ignore
    }

}
