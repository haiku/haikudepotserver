/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import org.haiku.haikudepotserver.job.model.JobSnapshot;

/**
 * <p>Utility class containing static helper methods for jobs.</p>
 */

public class Jobs {

    public static boolean isQueuedOrStarted(JobSnapshot job) {
        return switch (job.getStatus()) {
            case QUEUED, STARTED -> true;
            default -> false;
        };

    }

}
