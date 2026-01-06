/*
 * Copyright 2014-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import com.google.common.io.ByteSource;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.*;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>This implementation of {@link JobService} has some
 * jobs in "suspended" state so that tests of the JobApi are able to
 * query that known state.</p>
 */

public class TestJobServiceImpl implements JobService {

    public final static Instant DT_1976_JAN = LocalDateTime.of(1976,1,1,0,0).toInstant(ZoneOffset.UTC);
    public final static Instant DT_1976_FEB = LocalDateTime.of(1976,2,1,0,0).toInstant(ZoneOffset.UTC);
    public final static Instant DT_1976_MAR = LocalDateTime.of(1976,3,1,0,0).toInstant(ZoneOffset.UTC);
    public final static Instant DT_1976_APR = LocalDateTime.of(1976,4,1,0,0).toInstant(ZoneOffset.UTC);
    public final static Instant DT_1976_JUN = LocalDateTime.of(1976,6,1,0,0).toInstant(ZoneOffset.UTC);
    public final static Instant DT_1976_JUL = LocalDateTime.of(1976,6,1,0,0).toInstant(ZoneOffset.UTC);

    private Job queuedJob;
    private Job startedJob;
    private Job finishedJob;

    public TestJobServiceImpl() {
        super();
    }

    @PostConstruct
    public void init() {

        {
            queuedJob = new Job();
            queuedJob.setQueuedTimestamp(new Date(DT_1976_JAN.toEpochMilli()));
            queuedJob.setJobSpecification(new TestJobSpecificationImpl(null,"queued"));
        }

        {
            startedJob = new Job();
            startedJob.setQueuedTimestamp(new Date(DT_1976_FEB.toEpochMilli()));
            startedJob.setStartTimestamp(new Date(DT_1976_MAR.toEpochMilli()));
            startedJob.setJobSpecification(new TestJobSpecificationImpl(null,"started"));
        }

        {
            finishedJob = new Job();
            finishedJob.setQueuedTimestamp(new Date(DT_1976_APR.toEpochMilli()));
            finishedJob.setStartTimestamp(new Date(DT_1976_JUN.toEpochMilli()));
            finishedJob.setFinishTimestamp(new Date(DT_1976_JUL.toEpochMilli()));
            finishedJob.setJobSpecification(new TestJobSpecificationImpl("testuser", "finished"));
        }

    }

    @Override
    public String submit(
            JobSpecification specification,
            Set<JobSnapshot.Status> coalesceForStatuses) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String immediate(JobSpecification specification, boolean coalesceFinished) {
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
        return switch (guid) {
            case "queued" -> Optional.of(queuedJob);
            case "started" -> Optional.of(startedJob);
            case "finished" -> Optional.of(finishedJob);
            default -> Optional.empty();
        };

    }

    @Override
    public void removeJob(String guid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setJobProgressPercent(String guid, Integer progressPercent) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clearExpiredJobs() {
        // do nothing.
    }

    private boolean matches(JobSnapshot job, User user, Set<JobSnapshot.Status> statuses) {
        if (null != user) {
            if (null == job.getOwnerUserNickname()) {
                return false;
            }

            if(!job.getOwnerUserNickname().equals(user.getNickname())) {
                return false;
            }
        }

        if (null != statuses) {
            return statuses.contains(job.getStatus());
        }

        return true;
    }

    private List<Job> filteredJobs(final User user, final Set<JobSnapshot.Status> statuses) {
        return Stream.of(queuedJob, startedJob, finishedJob)
                .filter(j -> matches(j, user, statuses))
                .collect(Collectors.toList());
    }

    @Override
    public List<? extends JobSnapshot> findJobs(User user, Set<JobSnapshot.Status> statuses, int offset, int limit) {
        List<Job> result = filteredJobs(user, statuses);

        if (offset >= result.size()) {
            return Collections.emptyList();
        }

        Collections.sort(result);

        if (offset + limit > result.size()) {
            limit = result.size() - offset;
        }

        return result.subList(offset, offset+limit);
    }

    @Override
    public int totalJobs(User user, Set<JobSnapshot.Status> statuses) {
        return filteredJobs(user, statuses).size();
    }

    @Override
    public JobDataWithByteSink storeGeneratedData(String jobGuid, String useCode, String mediaTypeCode, JobDataEncoding encoding) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public JobData storeSuppliedData(String useCode, String mediaTypeCode, JobDataEncoding encoding, ByteSource byteSource) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String deriveDataFilename(String guid) {
        return "file.dat";
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid) {
        return Optional.empty();
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForSuppliedData(String suppliedDataGuid) {
        return Optional.empty();
    }

    @Override
    public Optional<JobData> tryGetData(String guid) {
        return Optional.empty();
    }

    @Override
    public Optional<JobDataWithByteSource> tryObtainData(String guid) throws IOException {
        return Optional.empty();
    }

    @Override
    public boolean awaitJobFinishedUninterruptibly(String guid, long timeout) {
        return true;
    }

    @Override
    public boolean awaitAllJobsFinishedUninterruptibly(long timeout) {
        return true;
    }
}
