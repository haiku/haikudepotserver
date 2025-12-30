/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.collect.Streams;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.ObjectContext;
import org.apache.cayenne.ResultBatchIterator;
import org.apache.cayenne.configuration.server.ServerRuntime;
import org.apache.cayenne.exp.Expression;
import org.apache.cayenne.exp.ExpressionFactory;
import org.apache.cayenne.exp.property.BaseProperty;
import org.apache.cayenne.exp.property.DateProperty;
import org.apache.cayenne.query.ObjectSelect;
import org.apache.cayenne.query.Ordering;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.dataobjects.*;
import org.haiku.haikudepotserver.job.model.JobServiceException;
import org.haiku.haikudepotserver.job.model.JobServiceStateTransitionException;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.support.db.PgAdvisoryLockHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

/**
 * This class contains a set of static methods that are used from the {@link DbDistributedJob2ServiceImpl}.
 */

public class DbDistributedJob2Helper {

    private static final Logger LOGGER = LoggerFactory.getLogger(DbDistributedJob2Helper.class);

    private final static int GET_NEXT_AVAILABLE_JOB_ATTEMPTS = 3;
    private final static Duration GET_NEXT_AVAILABLE_JOB_DELAY = Duration.ofMillis(500);

    private final static long PG_ADVISORY_LOCK_KEY = 2513234852114898L;

    /**
     * <p>This query will find the next available job but does not lock it.</p>
     */
    private final static String SQL_NEXT_AVAILABLE_JOB_CODE = """
              SELECT j2.code FROM job2.job j2
              WHERE 1 = 1
                AND j2.queue_timestamp IS NOT NULL
                AND j2.start_timestamp IS NULL
                AND j2.cancel_timestamp IS NULL
                AND j2.fail_timestamp IS NULL
                AND j2.finish_timestamp IS NULL
              ORDER BY j2.queue_timestamp ASC, j2.create_timestamp ASC
              LIMIT 1
              """;

    /**
     * <p>This query will lock the nominated job.</p>
     */
    private final static String SQL_LOCK_JOB_CODE = """
              SELECT ja1.code FROM job2.job_assignment ja1 WHERE ja1.code = ?
              FOR UPDATE SKIP LOCKED
            """;

    /**
     * <p>This query will find those Jobs which have been started and have not completed and
     * are not locked. This indicates a job that was started but maybe the thread or JVM
     * executing the job failed in some way.</p>
     */

    private final static String SQL_STARTED_NOT_LOCKED_JOB_CODES = """
            WITH jcodes AS (SELECT j2.code FROM job2.job j2
            WHERE 1 = 1
                AND j2.queue_timestamp IS NOT NULL
                AND j2.start_timestamp IS NOT NULL
                AND j2.cancel_timestamp IS NULL
                AND j2.fail_timestamp IS NULL
                AND j2.finish_timestamp IS NULL
            )
            SELECT ja1.code FROM job2.job_assignment ja1 WHERE ja1.code IN (SELECT jc3.code FROM jcodes jc3)
            FOR UPDATE SKIP LOCKED
            """;

    private final static List<Ordering> SORTS_STATE_TIMESTAMPS = List.of(
            Job.FINISH_TIMESTAMP.desc(),
            Job.START_TIMESTAMP.desc(),
            Job.QUEUE_TIMESTAMP.desc(),
            Job.FAIL_TIMESTAMP.desc(),
            Job.CANCEL_TIMESTAMP.desc(),
            Job.CREATE_TIMESTAMP.desc(),
            Job.CODE.desc());

    /**
     * <p>Takes out a transactional advisory lock on the database. This will be retained while the {@link Connection}'s
     * transaction is in progress. Typically, Job execution threads will take this out shared and any destructive
     * operations will take this out exclusive (not shared).</p>
     *
     * @return true if the lock was acquired within the duration specified.
     */
    public static boolean tryTransactionalAdvisoryLock(Connection connection, boolean shared, Duration timeout) throws SQLException {
        if (connection.getAutoCommit()) {
            throw new IllegalStateException("trying to acquire an advisory lock for job system outside transaction");
        }

        return PgAdvisoryLockHelper.tryTransactionalAdvisoryLock(connection, PG_ADVISORY_LOCK_KEY, shared, timeout);
    }

    /**
     * <p>Finds the next Job to be run and returns its <code>code</code> value. This will also lock the row on the
     * <code>job_assignment</code> table in the supplied {@link Connection} transaction so that other threads or
     * JVMs don't pick up the same Job.</p>
     */

    public static Optional<String> tryGetNextAvailableJobCode(Connection connection) {
        Preconditions.checkNotNull(connection);

        try {
            if (connection.getAutoCommit()) {
                throw new JobServiceException("the connection must be transactional; not auto-commit");
            }
        } catch (SQLException se) {
            throw new JobServiceException("problems checking the connection is transactional; not auto-commit", se);
        }

        int attempts = GET_NEXT_AVAILABLE_JOB_ATTEMPTS;

        while (attempts >= 0) {
            Optional<String> candidateJobCode = tryGetNextAvailableJobCodeWithoutLock(connection);

            if (candidateJobCode.isEmpty()) {
                return Optional.empty();
            }

            String jobCode = candidateJobCode.get();

            if (tryLockJobCodeForUpdate(connection, jobCode)) {
                LOGGER.info("did acquire lock on job [{}]", jobCode);
                return Optional.of(jobCode);
            }

            LOGGER.info("unable to acquire lock on job [{}]; will try to find next job again", jobCode);
            Uninterruptibles.sleepUninterruptibly(GET_NEXT_AVAILABLE_JOB_DELAY);
            attempts --;
        }

        LOGGER.info("attempted to find next job {} times; aborting", GET_NEXT_AVAILABLE_JOB_ATTEMPTS);

        return Optional.empty();
    }

    /**
     * Get a count of those Jobs that are not yet finished, failed or cancelled.
     */

    public static long countNotFinished(ObjectContext context) {
        return ObjectSelect.query(Job.class)
                .where(hasCompletedStatusExpression().notExp())
                .selectCount(context);
    }

    /**
     * @return true if the Job associated with the supplied <code>jobCode</code> is finished.
     */

    public static boolean isFinished(ObjectContext context, String jobCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");
        long count = ObjectSelect.query(Job.class)
                .where(Job.CODE.eq(jobCode).andExp(hasCompletedStatusExpression()))
                .selectCount(context);

        if (0 == count) {
            return false;
        }

        if (1 == count) {
            return true;
        }

        throw new IllegalStateException("more than one job found for [" + jobCode + "]");
    }

    /**
     * <p>Deletes the {@link Job} and any associated objects.</p>
     */
    public static boolean deleteJob(ObjectContext objectContext, String jobCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");

        Job job = Job.tryGetByCode(objectContext, jobCode).orElse(null);

        if (null == job) {
            return false;
        }

        objectContext.deleteObject(job.getJobAssignment());
        objectContext.deleteObjects(job.getJobDatas());
        objectContext.deleteObject(job);

        return true;
    }

    /**
     * @return true if the percentage was changed.
     */
    public static boolean setJobProgressPercent(ObjectContext objectContext, String jobCode, Integer progressPercent) {
        Preconditions.checkNotNull(objectContext, "the object context must be supplied");
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");

        if (null != progressPercent && (progressPercent < 0 || progressPercent > 100)) {
            throw new IllegalStateException("bad progress percent [" + progressPercent + "]");
        }

        Job job = Job.getByCode(objectContext, jobCode);
        Integer existingPercentage = job.getProgressPercent();

        switch (mapStateToStatus(job)) {
            case FINISHED:
                if (null == existingPercentage || !existingPercentage.equals(progressPercent)) {
                    job.setProgressPercent(100);
                    return true;
                }
                break;
            case STARTED:
                if (null == existingPercentage || !existingPercentage.equals(progressPercent)) {
                    job.setProgressPercent(progressPercent);
                    return true;
                }
                break;
        }

        return false;
    }

    /**
     * <p>Goes through all the Jobs which have reached their expiry. It will delete each of them.</p>
     * @return the quantity of Jobs deleted.
     */

    public static long clearExpiredJobs(ServerRuntime serverRuntime, Instant now) {
        Preconditions.checkArgument(null != now, "the now instant must be supplied");

        try (ResultBatchIterator<Object[]> jobCodeRowBatchIterator = ObjectSelect.query(Job.class)
                .where(Job.EXPIRY_TIMESTAMP.lt(new java.sql.Timestamp(now.toEpochMilli())))
                .fetchDataRows()
                .columns(Job.CODE)
                .batchIterator(serverRuntime.newContext(), 100)) {
            return deleteJobsByJobCodeRow(serverRuntime, jobCodeRowBatchIterator);
        }
    }

    /**
     * <p>Goes through all the jobs which have reached their expiry and have already completed. It will delete
     * each of them.</p>
     * @return the quantity of Jobs deleted.
     */

    public static long clearCompletedExpiredJobs(ServerRuntime serverRuntime, Instant now) {
        Preconditions.checkArgument(null != now, "the now instant must be supplied");

        try (ResultBatchIterator<Object[]> jobCodeRowBatchIterator = ObjectSelect.query(Job.class)
                .where(Job.EXPIRY_TIMESTAMP.lt(new java.sql.Timestamp(now.toEpochMilli()))
                        .andExp(hasCompletedStatusExpression()))
                .fetchDataRows()
                .columns(Job.CODE)
                .batchIterator(serverRuntime.newContext(), 100)) {
            return deleteJobsByJobCodeRow(serverRuntime, jobCodeRowBatchIterator);
        }
    }

    public static List<Job> findJobs(ObjectContext objectContext, String userNickname, Set<JobSnapshot.Status> statuses, int offset, int limit) {
        Preconditions.checkNotNull(objectContext, "the object context must be supplied");
        Preconditions.checkArgument(offset >= 0, "illegal offset value");

        if (limit < 1) {
            throw new IllegalArgumentException("the limit %d must be > 1".formatted(limit));
        }

        return ObjectSelect.query(Job.class)
                .where(expressionForUserNicknameAndStatuses(userNickname, statuses))
                .orderBy(Job.CREATE_TIMESTAMP.desc())
                .offset(offset)
                .limit(limit)
                .select(objectContext);
    }

    public static long totalJobs(ObjectContext objectContext, String userNickname, Set<JobSnapshot.Status> statuses) {
        Preconditions.checkNotNull(objectContext, "the object context must be supplied");
        return ObjectSelect.query(Job.class)
                .where(expressionForUserNicknameAndStatuses(userNickname, statuses))
                .selectCount(objectContext);
    }

    /**
     * <p>Makes sure that the database has an entry stored for the given Media Type.</p>
     */

    public static void ensureJobDataMediaType(ObjectContext objectContext, String mediaTypeCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mediaTypeCode), "the media type code must be supplied");
        getOrCreateJpaJobDataMediaType(objectContext, mediaTypeCode);
    }

    public static JobData createGeneratedJobData(
            ObjectContext objectContext,
            String jobCode,
            String jobDataCode,
            String useCode,
            String mediaTypeCode,
            String encodingCode) {
        return createJobData(
                objectContext,
                jobCode,
                jobDataCode,
                useCode,
                mediaTypeCode,
                encodingCode,
                JobDataType.CODE_GENERATED);
    }

    public static JobData createSuppliedJobData(
            ObjectContext objectContext,
            String jobDataCode,
            String useCode,
            String mediaTypeCode,
            String encodingCode) {
        return createJobData(
                objectContext,
                null,
                jobDataCode,
                useCode,
                mediaTypeCode,
                encodingCode,
                JobDataType.CODE_SUPPLIED);
    }

    /**
     * Updates the status of the Job. Only some transitions from one state to another are supported. If an illegal
     * state transition is attempted, this method will throw {@link JobServiceStateTransitionException}.
     */

    public static void updateJobStatus(ObjectContext objectContext, String jobCode, Instant now, JobSnapshot.Status targetStatus) {
        Preconditions.checkArgument(StringUtils.isNotBlank(jobCode));
        Preconditions.checkArgument(null != now, "the now instant must be supplied");
        Preconditions.checkArgument(null != targetStatus, "the targetStatus must be supplied");

        Job job = Job.getByCode(objectContext, jobCode);
        JobSnapshot.Status currentStatus = mapStateToStatus(job);
        java.sql.Timestamp nowTimestamp = new java.sql.Timestamp(now.toEpochMilli());

        if (currentStatus != targetStatus) {
            switch (targetStatus) {
                case QUEUED:
                    if (currentStatus == JobSnapshot.Status.INDETERMINATE) {
                        job.setQueueTimestamp(nowTimestamp);
                        job.setProgressPercent(null);
                    } else {
                        throw new JobServiceStateTransitionException(currentStatus, targetStatus);
                    }
                    break;

                case STARTED:
                    if (currentStatus == JobSnapshot.Status.QUEUED) {
                        job.setStartTimestamp(nowTimestamp);
                        job.setProgressPercent(0);
                    } else {
                        throw new JobServiceStateTransitionException(currentStatus, targetStatus);
                    }
                    break;

                case FINISHED:
                    if (currentStatus == JobSnapshot.Status.STARTED) {
                        job.setFinishTimestamp(nowTimestamp);
                        job.setProgressPercent(100);
                    } else {
                        throw new JobServiceStateTransitionException(currentStatus, targetStatus);
                    }
                    break;

                case FAILED:
                    job.setFailTimestamp(nowTimestamp);
                    break;

                case CANCELLED:
                    job.setCancelTimestamp(nowTimestamp);
                    break;

                default:
                    throw new JobServiceStateTransitionException(currentStatus, targetStatus);
            }
        }
    }

    /**
     * <p>It could be that jobs have started but failed; left in started state. In such a case the `job` row
     * won't be locked but will be started.</p>
     */
    public static Set<String> getDanglingStartedJobCodes(Connection connection) throws SQLException {
        Set<String> result = new HashSet<>();
        try (
                PreparedStatement statement = connection.prepareStatement(SQL_STARTED_NOT_LOCKED_JOB_CODES);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                result.add(resultSet.getString(1));
            }
        }
        return Collections.unmodifiableSet(result);
    }

    // TODO; although the API allows streaming, the data is not streamed.
    public static Stream<Job> streamJobsByTypeAndStatuses(
            ObjectContext objectContext,
            String jobTypeCode,
            Set<JobSnapshot.Status> statuses) {
        Preconditions.checkNotNull(objectContext, "the object context must be supplied");
        Preconditions.checkArgument(null != jobTypeCode, "the job type code must be supplied");

        JobType jpaJobType = JobType.tryGetByCode(objectContext, jobTypeCode).orElse(null);

        // it could be that nobody has run a report of this type yet.

        if (null == jpaJobType) {
            return Stream.empty();
        }

        Expression jobTypeExpression = Job.JOB_TYPE.dot(JobType.CODE).eq(jobTypeCode);

        // This would be way more efficient as a SQL statement, but this is quite comprehensible using JPA. The idea
        // is that we want to get the Jobs in a distinct order; the finished ones, the started ones, the queued ones
        // and then anything else.

        List<Job> result = new ArrayList<>();

        if (statuses.contains(JobSnapshot.Status.FINISHED)) {
            result.addAll(
                    ObjectSelect.query(Job.class)
                            .where(jobTypeExpression.andExp(hasStatusExpression(JobSnapshot.Status.FINISHED)))
                            .orderBy(Job.FINISH_TIMESTAMP.desc(), Job.CODE.desc())
                            .select(objectContext)
                            );
        }

        if (statuses.contains(JobSnapshot.Status.STARTED)) {
            result.addAll(
                    ObjectSelect.query(Job.class)
                            .where(jobTypeExpression.andExp(hasStatusExpression(JobSnapshot.Status.STARTED)))
                            .orderBy(Job.START_TIMESTAMP.desc(), Job.CODE.desc())
                            .select(objectContext)
                            );
        }

        if (statuses.contains(JobSnapshot.Status.QUEUED)) {
            result.addAll(
                    ObjectSelect.query(Job.class)
                            .where(jobTypeExpression.andExp(hasStatusExpression(JobSnapshot.Status.QUEUED)))
                            .orderBy(Job.QUEUE_TIMESTAMP.desc(), Job.CODE.desc())
                            .select(objectContext)
                            );
        }

        Set<JobSnapshot.Status> remainingStatuses = SetUtils.difference(
                statuses,
                Set.of(JobSnapshot.Status.FINISHED, JobSnapshot.Status.STARTED, JobSnapshot.Status.QUEUED));

        if (!remainingStatuses.isEmpty()) {
            result.addAll(
                    ObjectSelect.query(Job.class)
                            .where(jobTypeExpression.andExp(ExpressionFactory.or(
                                    remainingStatuses.stream().map(DbDistributedJob2Helper::hasStatusExpression).toList()
                            )))
                            .orderBy(SORTS_STATE_TIMESTAMPS)
                            .select(objectContext)
            );
        }

        return result.stream();
    }

    public static void createJob(
            ObjectContext objectContext,
            String code,
            String jobTypeCode,
            String ownerUserNickname,
            Instant now,
            long ttlMillis,
            JsonNode specificationSerialized,
            Collection<String> suppliedDataCodes,
            boolean started
    ) {
        Preconditions.checkArgument(null != code, "the job code must be supplied");
        Preconditions.checkArgument(null != jobTypeCode, "the job type code must be supplied");
        Preconditions.checkArgument(null != now, "the now instant must be supplied");
        Preconditions.checkArgument(ttlMillis >= 0, "invalid ttl millis");
        Preconditions.checkArgument(null != specificationSerialized, "the job specification must be supplied");

        java.sql.Timestamp nowTimestamp = new java.sql.Timestamp(now.toEpochMilli());

        JobAssignment jobAssignment = objectContext.newObject(JobAssignment.class);
        jobAssignment.setCode(code);

        Job job = objectContext.newObject(Job.class);

        if (started) {
            job.setStartTimestamp(nowTimestamp);
        }

        job.setQueueTimestamp(nowTimestamp);
        job.setCode(code);
        job.setOwnerUserNickname(ownerUserNickname);
        job.setJobType(getOrCreateJobType(objectContext, jobTypeCode));
        job.setExpiryTimestamp(new java.sql.Timestamp(now.toEpochMilli() + ttlMillis));
        job.setSpecification(specificationSerialized.toString());
        job.setJobAssignment(jobAssignment);

        // attach all the supplied data to the job

        for (String suppliedDataCode : CollectionUtils.emptyIfNull(suppliedDataCodes)) {
            JobData jobData = JobData.getByCode(objectContext, suppliedDataCode);

            if (null != jobData.getJob()) {
                throw new IllegalStateException(String.format("the supplied data [%s] is already attached to a job", suppliedDataCode));
            }

            jobData.setJob(job);
        }
    }

    private static Expression expressionForUserNicknameAndStatuses(String userNickname, Set<JobSnapshot.Status> statuses) {
        List<Expression> result = new ArrayList<>();

        result.add(ExpressionFactory.expTrue());

        if (StringUtils.isNotBlank(userNickname)) {
            result.add(Job.OWNER_USER_NICKNAME.eq(userNickname));
        }

        result.addAll(
                CollectionUtils.emptyIfNull(statuses)
                        .stream()
                        .map(DbDistributedJob2Helper::hasStatusExpression)
                        .toList()
        );

        return ExpressionFactory.and(result);
    }

    private static JobType getOrCreateJobType(ObjectContext objectContext, String jobTypeCode) {
        String code = jobTypeCode.toLowerCase();
        return JobType.tryGetByCode(objectContext, code)
                .orElseGet(() -> {
                    JobType result = objectContext.newObject(JobType.class);
                    result.setCode(code);
                    return result;
                });
    }

    private static JobDataMediaType getOrCreateJpaJobDataMediaType(ObjectContext objectContext, String jobDataMediaTypeCode) {
        return JobDataMediaType.tryGetByCode(objectContext, jobDataMediaTypeCode)
                .orElseGet(() -> {
                    JobDataMediaType result = objectContext.newObject(JobDataMediaType.class);
                    result.setCode(jobDataMediaTypeCode);
                    return result;
                });
    }

    private static JobData createJobData(
            ObjectContext objectContext,
            String jobCode,
            String jobDataCode,
            String useCode,
            String mediaTypeCode,
            String encodingCode,
            String jobDataTypeCode) {

        Preconditions.checkArgument(jobDataTypeCode.equals(JobDataType.CODE_SUPPLIED) || StringUtils.isNotBlank(jobCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(jobDataCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(mediaTypeCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(encodingCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(jobDataTypeCode));

        JobData jobData = objectContext.newObject(JobData.class);

        if (StringUtils.isNotBlank(jobCode)) {
            jobData.setJob(Job.getByCode(objectContext, jobCode));
        }

        jobData.setCode(jobDataCode);
        jobData.setUseCode(useCode);
        jobData.setStorageCode(jobDataCode);
        jobData.setJobDataMediaType(getOrCreateJpaJobDataMediaType(objectContext, mediaTypeCode));
        jobData.setJobDataEncoding(JobDataEncoding.getByCode(objectContext, encodingCode));
        jobData.setJobDataType(JobDataType.getByCode(objectContext, jobDataTypeCode));

        return jobData;
    }

    private static long deleteJobsByJobCodeRow(ServerRuntime serverRuntime, ResultBatchIterator<Object[]> jobCodeRowBatchIterator) {
        long count = 0;

        while (jobCodeRowBatchIterator.hasNext()) {
            List<String> jobCodes = jobCodeRowBatchIterator.next()
                    .stream()
                    .map(row -> row[0].toString())
                    .toList();

            for (String jobCode : jobCodes) {
                ObjectContext objectContext = serverRuntime.newContext();
                if (deleteJob(objectContext, jobCode)) {
                    objectContext.commitChanges();
                    count++;
                    LOGGER.info("did delete job [{}]", jobCode);
                } else {
                    LOGGER.warn("unable to delete job [{}]", jobCode);
                }
            }
        }

        return count;
    }

    private static JobSnapshot.Status mapStateToStatus(Job job) {
        return Jobs.mapTimestampsToStatus(
                Optional.ofNullable(job.getFailTimestamp()).map(Timestamp::toInstant).orElse(null),
                Optional.ofNullable(job.getCancelTimestamp()).map(Timestamp::toInstant).orElse(null),
                Optional.ofNullable(job.getFinishTimestamp()).map(Timestamp::toInstant).orElse(null),
                Optional.ofNullable(job.getStartTimestamp()).map(Timestamp::toInstant).orElse(null),
                Optional.ofNullable(job.getQueueTimestamp()).map(Timestamp::toInstant).orElse(null)
        );
    }

    private static Expression hasStatusExpression(JobSnapshot.Status status) {
        return switch (status) {
            case QUEUED -> hasTimestampsSetExpression(
                    List.of(Job.QUEUE_TIMESTAMP),
                    List.of(Job.START_TIMESTAMP, Job.FINISH_TIMESTAMP, Job.FAIL_TIMESTAMP, Job.CANCEL_TIMESTAMP)
            );
            case STARTED -> hasTimestampsSetExpression(
                    List.of(Job.QUEUE_TIMESTAMP, Job.START_TIMESTAMP),
                    List.of(Job.FINISH_TIMESTAMP, Job.FAIL_TIMESTAMP, Job.CANCEL_TIMESTAMP)
            );
            case FINISHED -> hasTimestampsSetExpression(
                    List.of(Job.QUEUE_TIMESTAMP, Job.START_TIMESTAMP, Job.FINISH_TIMESTAMP),
                    List.of(Job.FAIL_TIMESTAMP, Job.CANCEL_TIMESTAMP)
            );
            case FAILED -> hasTimestampsSetExpression(
                    List.of(Job.FAIL_TIMESTAMP),
                    List.of(Job.CANCEL_TIMESTAMP)
            );
            case CANCELLED -> Job.CANCEL_TIMESTAMP.isNotNull();
            case INDETERMINATE -> throw new IllegalStateException("not possible to get status for [" + status + "]");
        };

    }

    private static Expression hasTimestampsSetExpression(
            List<DateProperty<Timestamp>> notNullProperties,
            List<DateProperty<Timestamp>> nullProperties
    ) {
        return ExpressionFactory.and(Streams.concat(
                notNullProperties.stream().map(BaseProperty::isNotNull),
                nullProperties.stream().map(BaseProperty::isNull)
        ).toList());
    }

    private static Optional<String> tryGetNextAvailableJobCodeWithoutLock(Connection connection) {
        try (
                PreparedStatement statement = connection.prepareStatement(SQL_NEXT_AVAILABLE_JOB_CODE);
                ResultSet resultSet = statement.executeQuery()
        ) {
            if (resultSet.next()) {

                String jobCode = resultSet.getString(1);

                if (resultSet.next()) {
                    throw new IllegalStateException("getting the next available job; found more than one in result set");
                }

                return Optional.of(jobCode);
            }

            return Optional.empty();
        } catch (SQLException se) {
            throw new JobServiceException("unable to get the next available job code", se);
        }
    }

    private static boolean tryLockJobCodeForUpdate(Connection connection, String jobCode) {
        try (PreparedStatement statement = connection.prepareStatement(SQL_LOCK_JOB_CODE)) {
            statement.setString(1, jobCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException se) {
            throw new JobServiceException(String.format("unable to lock next job code [%s]", jobCode), se);
        }
    }

    private static Expression hasCompletedStatusExpression() {
        return Job.FAIL_TIMESTAMP.isNotNull()
                .orExp(Job.CANCEL_TIMESTAMP.isNotNull())
                .orExp(Job.FINISH_TIMESTAMP.isNotNull());
    }

}
