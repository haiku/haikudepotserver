/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job.model;

import java.util.Date;
import java.util.Set;

/**
 * <p>This interface describes an object that represents the state of a job at a particular point in time.</p>
 */

public interface JobSnapshot {

    public enum Status {
        INDETERMINATE,
        QUEUED,
        STARTED,
        FINISHED,
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

    Long getTimeToLive();

}
