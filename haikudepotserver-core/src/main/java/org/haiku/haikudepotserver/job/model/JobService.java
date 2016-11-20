/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

import com.google.common.io.ByteSource;
import org.haiku.haikudepotserver.dataobjects.User;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * <p>An implementation of this type of service is able to take submitted job specifications, couple each to
 * a runner and will run the specification.  It coordinates the running of jobs.</p>
 */

public interface JobService {

    /**
     * @return true if all jobs had finished in the specified timeout.
     */

    boolean awaitAllJobsFinishedUninterruptibly(long timeout);

    /**
     * <p>This method will block until the job is no longer queued or started.  It may be finished, but
     * actually it could have also failed or have been cancelled.</p>
     * @return true if the job had finished in the specified timeout.
     */

    boolean awaitJobFinishedUninterruptibly(String guid, long timeout);

    /**
     * <p>This method will run the specification asynchronously.  If there is an existing job which is
     * in the system and that is in one of the states supplied, then that will be returned instead.
     * This is to avoid double-running the job.  See {@link JobSpecification} for some handy constants.
     * </p>
     */

    String submit(
            JobSpecification specification,
            Set<JobSnapshot.Status> coalesceForStatuses);

    /**
     * <p>This method will run the specification immediate in the current thread.  Do not use this unless
     * there is a need to feed the data directly or quasi-directly to a client.  See
     * {@link #submit(JobSpecification, Set)} for information on the coalescing over statuses.</p>
     */

    String immediate(JobSpecification specification, boolean coalesceFinished);

    void setJobFailTimestamp(String guid);

    void setJobCancelTimestamp(String guid);

    /**
     * <P>This will return a clone of the {@link JobSnapshot}
     * as opposed to a 'working' object.</P>
     */

    Optional<? extends JobSnapshot> tryGetJob(String guid);

    void setJobProgressPercent(String guid, Integer progressPercent);

    void clearExpiredJobs();

    /**
     * <p>This method will return an ordered list of the
     * {@link JobSnapshot} that belong to the specified
     * user (or all users if this value is null).  It will return those
     * {@link JobSnapshot} objects that are from the
     * specified offset with a maximum of the specified limit.</p>
     * @param user only return {@link JobSnapshot} objects
     *             for this user.  If the user is null then return values for any user.
     * @param statuses only return {@link JobSnapshot}
     *                 objects that have the specified status.
     */

    List<? extends JobSnapshot> findJobs(User user, Set<JobSnapshot.Status> statuses, int offset, int limit);

    /**
     * <p>This method returns the count of packages that could be returned from
     * {@link #findJobs(User, java.util.Set, int, int)}
     * but without the offset and limits considered.</p>
     */

    int totalJobs(User user, Set<JobSnapshot.Status> statuses);

    /**
     * <p>Tries to identify a job which is associated with the job data guid supplied.</p>
     */

    Optional<? extends JobSnapshot> tryGetJobForData(String jobDataGuid);

    /**
     * <p>This method will return a suggested filename for the download of the job data associated
     * with the GUID.  If no data can be found, it will return an absent {@link Optional}.</p>
     */

    String deriveDataFilename(String guid);

    Optional<JobData> tryGetData(String guid);

    /**
     * <P>This method will provide a means of storing data against the GUID.  This would be called by
     * the job runner.</P>
     */

    JobDataWithByteSink storeGeneratedData(String jobGuid, String useCode, String mediaTypeCode) throws IOException;

    /**
     * <p>This method will <em>provide</em> some data for a job to run; input data.  This would be called by
     * code (such as web controller) prior to submitting a job to be run in order that the input data for the
     * job can be obtained when the job runs.</p>
     * @param useCode an identification for the data in terms of the report
     * @param mediaTypeCode is the type of the data; for example; text/csv
     * @param byteSource provides the data to be stored.
     * @return an identifier for the data to be referenced later when a job is loaded.
     */

    JobData storeSuppliedData(String useCode, String mediaTypeCode, ByteSource byteSource) throws IOException;

    /**
     * <p>This method will return a job data with an object that is able to provide the octets of the data.</p>
     */

    Optional<JobDataWithByteSource> tryObtainData(String guid) throws IOException;

}
