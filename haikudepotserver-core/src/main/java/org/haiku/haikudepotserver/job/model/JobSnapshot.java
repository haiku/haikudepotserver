/*
 * Copyright 2014-2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

import java.util.*;

/**
 * <p>This interface describes an object that represents the state of a job at a particular point in time.</p>
 */

public interface JobSnapshot {

    Set<Status> COALESCE_STATUSES_NONE = Collections.emptySet();
    Set<Status> COALESCE_STATUSES_QUEUED = EnumSet.of(Status.QUEUED);
    Set<Status> COALESCE_STATUSES_QUEUED_STARTED = EnumSet.of(Status.QUEUED, Status.STARTED);
    Set<Status> COALESCE_STATUSES_QUEUED_STARTED_FINISHED = EnumSet.of(Status.QUEUED, Status.STARTED, Status.FINISHED);

    enum Status {
        QUEUED,
        STARTED,
        FINISHED,
        INDETERMINATE,
        FAILED,
        CANCELLED
    }

    Date getStartTimestamp();

    Date getFinishTimestamp();

    Date getQueuedTimestamp();

    Date getFailTimestamp();

    Date getCancelTimestamp();

    Integer getProgressPercent();

    JobSpecification getJobSpecification();

    Set<String> getGeneratedDataGuids();

    Set<String> getDataGuids();

    Status getStatus();

    String getOwnerUserNickname();

    String getJobTypeCode();

    String getGuid();

    Optional<Long> tryGetTimeToLiveMillis();

}
