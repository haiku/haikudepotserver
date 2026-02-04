/*
 * Copyright 2025-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import com.google.common.io.ByteSource;
import org.haiku.haikudepotserver.dataobjects.User;
import org.haiku.haikudepotserver.job.model.*;

import java.io.IOException;
import java.util.*;

/**
 * <p>This is a special instance of {@link JobService} which does not actually run any jobs.</p>
 */

public class NoopJobServiceImpl implements JobService {

    @Override
    public Optional<? extends JobSnapshot> tryGetJob(String guid) {
        return Optional.empty();
    }

    @Override
    public void removeJob(String guid) {
    }

    @Override
    public void setJobProgressPercent(String guid, Integer progressPercent) {
    }

    @Override
    public void clearExpiredJobs() {
    }

    @Override
    public List<? extends JobSnapshot> findJobs(User user, Set<JobSnapshot.Status> statuses, int offset, int limit) {
        return List.of();
    }

    @Override
    public int totalJobs(User user, Set<JobSnapshot.Status> statuses) {
        return 0;
    }

    @Override
    public Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid) {
        return Optional.empty();
    }

    @Override
    public String deriveDataFilename(String guid) {
        return guid + ".dat";
    }

    @Override
    public Optional<JobData> tryGetData(String guid) {
        return Optional.empty();
    }

    @Override
    public JobDataWithByteSink storeGeneratedData(String jobGuid, String useCode, String mediaTypeCode, JobDataEncoding encoding) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public JobData storeSuppliedData(String useCode, String mediaTypeCode, JobDataEncoding encoding, ByteSource byteSource) throws IOException {
        throw new UnsupportedOperationException();
    }

    @Override
    public Optional<JobDataWithByteSource> tryObtainData(String guid) throws IOException {
        return Optional.empty();
    }

    @Override
    public boolean awaitAllJobsFinishedUninterruptibly(long timeout) {
        return true;
    }

    @Override
    public boolean awaitJobFinishedUninterruptibly(String guid, long timeout) {
        return true;
    }

    @Override
    public String submit(JobSpecification specification, Set<JobSnapshot.Status> coalesceForStatuses) {
        return UUID.randomUUID().toString();
    }

    @Override
    public String immediate(JobSpecification specification, boolean coalesceFinished) {
        return UUID.randomUUID().toString();
    }

    @Override
    public void setJobFailTimestamp(String guid) {
        /* ignore */
    }

    @Override
    public void setJobCancelTimestamp(String guid) {
        /* ignore */
    }
}
