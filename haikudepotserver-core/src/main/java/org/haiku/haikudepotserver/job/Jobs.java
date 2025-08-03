/*
 * Copyright 2014-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import com.google.common.base.Strings;
import com.google.common.net.MediaType;
import org.haiku.haikudepotserver.job.model.JobData;
import org.haiku.haikudepotserver.job.model.JobSnapshot;

import java.time.Instant;

/**
 * <p>Utility class containing static helper methods for jobs.</p>
 */

public class Jobs {

    final static String EXTENSION_DAT = "dat";

    public static boolean isQueuedOrStarted(JobSnapshot job) {
        return switch (job.getStatus()) {
            case QUEUED, STARTED -> true;
            default -> false;
        };

    }

    /**
     * <p>From the supplied timestamps, it is possible to derive a status.</p>
     */

    public static JobSnapshot.Status mapTimestampsToStatus(
            Instant failTimestamp,
            Instant cancelTimestamp,
            Instant finishTimestamp,
            Instant startTimestamp,
            Instant queueTimestamp
    ) {
        if (null != failTimestamp) {
            return JobSnapshot.Status.FAILED;
        }
        if (null != cancelTimestamp) {
            return JobSnapshot.Status.CANCELLED;
        }
        if (null != finishTimestamp) {
            return JobSnapshot.Status.FINISHED;
        }
        if (null != startTimestamp) {
            return JobSnapshot.Status.STARTED;
        }
        if (null != queueTimestamp) {
            return JobSnapshot.Status.QUEUED;
        }
        return JobSnapshot.Status.INDETERMINATE;
    }

    /**
     * <p>Returns a file extension for the {@link JobData} supplied.</p>
     */

    static String deriveExtension(JobData jobData) {
        String extensionWithoutEncoding = deriveExtensionWithoutEncoding(jobData);
        return switch (jobData.getEncoding()) {
            case NONE -> extensionWithoutEncoding;
            case GZIP -> extensionWithoutEncoding + ".gz";
        };
    }

    private static String deriveExtensionWithoutEncoding(JobData jobData) {
        if (!Strings.isNullOrEmpty(jobData.getMediaTypeCode())) {
            MediaType jobDataMediaTypeNoParams = MediaType.parse(jobData.getMediaTypeCode()).withoutParameters();

            if (jobDataMediaTypeNoParams.equals(MediaType.CSV_UTF_8.withoutParameters())) {
                return "csv";
            }

            if(jobDataMediaTypeNoParams.equals(MediaType.ZIP.withoutParameters())) {
                return "zip";
            }

            if(jobDataMediaTypeNoParams.equals(MediaType.TAR.withoutParameters())) {
                return "tar";
            }

            if(jobDataMediaTypeNoParams.equals(MediaType.PLAIN_TEXT_UTF_8.withoutParameters())) {
                return "txt";
            }

            if(jobDataMediaTypeNoParams.equals(MediaType.JSON_UTF_8.withoutParameters())) {
                return "json";
            }
        }

        return EXTENSION_DAT;
    }
}
