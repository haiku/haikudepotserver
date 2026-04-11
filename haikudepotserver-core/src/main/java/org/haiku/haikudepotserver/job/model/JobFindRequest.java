/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.model;

import jakarta.annotation.Nullable;

import java.util.Set;

/**
 * <p>This model carries the data that specify the search in the method
 * {@link JobService#findJobs}</p>
 *
 * @param ownerUserNickname only return {@link JobSnapshot} objects
 *                          for this user.  If the user is null then return values for any user.
 * @param statuses          only return {@link JobSnapshot}
 *                          objects that have the specified status.
 */

public record JobFindRequest(
        @Nullable String ownerUserNickname,
        @Nullable String jobTypeCode,
        @Nullable Set<JobSnapshot.Status> statuses
) {
}
