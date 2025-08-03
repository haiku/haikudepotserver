/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.model;

/**
 * <p>This exception is thrown when there is a problem transitioning a job from one state to
 * another. For example, a transition from "Failed" to "Queued" is not allowed.</p>
 */

public class JobServiceStateTransitionException extends JobServiceException {

    public JobServiceStateTransitionException(
            JobSnapshot.Status currentStatus,
            JobSnapshot.Status newStatus
    ) {
        super("cannot transition from [" + currentStatus + "] to [" + newStatus + "]");
    }
}
