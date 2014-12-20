/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.job;

import org.haikuos.haikudepotserver.job.model.JobSnapshot;

/**
 * <p>Utility class containing static helper methods for jobs.</p>
 */

public class Jobs {

    public static boolean isQueuedOrStarted(JobSnapshot job) {
        switch(job.getStatus()) {
            case QUEUED:
            case STARTED:
                return true;
        }

        return false;
    }

}
