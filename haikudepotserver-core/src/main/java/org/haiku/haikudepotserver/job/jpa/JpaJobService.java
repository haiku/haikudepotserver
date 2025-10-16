/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.jpa;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.SetUtils;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.Jobs;
import org.haiku.haikudepotserver.job.jpa.model.*;
import org.haiku.haikudepotserver.job.model.JobServiceException;
import org.haiku.haikudepotserver.job.model.JobServiceStateTransitionException;
import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.support.exception.ObjectNotFoundException;
import org.haiku.haikudepotserver.support.jpa.OffsetBasedPageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;

/**
 * <p>Functions in relation to operations around job data in the database. This is not
 * able to run jobs; only operates on the data.</p>
 */

@Service
public class JpaJobService {

    private final static Sort.Order ORDER_ID = Sort.Order.desc(String.join(".", Job_.STATE, JobState_.ID));

    private final static String SQL_NEXT_AVAILABLE_JOB_ID = """
              WITH jids AS (SELECT j2.id FROM job.job j2
                JOIN job.job_state j2_state ON j2_state.id = j2.job_state_id
              WHERE 1 = 1
                AND j2_state.queue_timestamp IS NOT NULL
                AND j2_state.start_timestamp IS NULL
                AND j2_state.cancel_timestamp IS NULL
                AND j2_state.fail_timestamp IS NULL
                AND j2_state.finish_timestamp IS NULL
              ORDER BY j2_state.queue_timestamp ASC, j2.create_timestamp ASC
              LIMIT 1
              )
              SELECT j1.id FROM job.job j1 WHERE j1.id = (SELECT j3.id FROM jids j3)
              FOR UPDATE SKIP LOCKED
            """;

    private final static String SQL_STARTED_NOT_LOCKED_JOB_CODES = """
            WITH jids AS (SELECT j2.id FROM job.job j2
                JOIN job.job_state j2_state ON j2_state.id = j2.job_state_id
            WHERE 1 = 1
                AND j2_state.queue_timestamp IS NOT NULL
                AND j2_state.start_timestamp IS NOT NULL
                AND j2_state.cancel_timestamp IS NULL
                AND j2_state.fail_timestamp IS NULL
                AND j2_state.finish_timestamp IS NULL
            )
            SELECT j1.code FROM job.job j1 WHERE j1.id IN (SELECT j3.id FROM jids j3)
            FOR UPDATE SKIP LOCKED 
            """;

    private final static Sort SORTS_STATE_TIMESTAMPS = Sort.by(
            Sort.Order.desc(String.join(".", Job_.STATE, JobState_.FINISH_TIMESTAMP)),
            Sort.Order.desc(String.join(".", Job_.STATE, JobState_.START_TIMESTAMP)),
            Sort.Order.desc(String.join(".", Job_.STATE, JobState_.QUEUE_TIMESTAMP)),
            Sort.Order.desc(String.join(".", Job_.STATE, JobState_.FAIL_TIMESTAMP)),
            Sort.Order.desc(String.join(".", Job_.STATE, JobState_.CANCEL_TIMESTAMP)),
            Sort.Order.desc(String.join(".", Job_.STATE, JobState_.CREATE_TIMESTAMP)),
            ORDER_ID
    );

    private final JdbcTemplate jdbcTemplate;

    private final JobRepository jobRepository;

    private final JobStateRepository jobStateRepository;

    private final JobSuppliedDataRepository jobSuppliedDataRepository;

    private final JobTypeRepository jobTypeRepository;

    private final JobDataMediaTypeRepository jobDataMediaTypeRepository;

    private final JobDataEncodingRepository jobDataEncodingRepository;

    private final JobSpecificationRepository jobSpecificationRepository;
    private final JobGeneratedDataRepository jobGeneratedDataRepository;

    public JpaJobService(
            JdbcTemplate jdbcTemplate,
            JobRepository jobRepository,
            JobStateRepository jobStateRepository,
            JobSuppliedDataRepository jobSuppliedDataRepository,
            JobTypeRepository jobTypeRepository,
            JobDataMediaTypeRepository jobDataMediaTypeRepository,
            JobDataEncodingRepository jobDataEncodingRepository,
            JobSpecificationRepository jobSpecificationRepository,
            JobGeneratedDataRepository jobGeneratedDataRepository) {
        this.jdbcTemplate = jdbcTemplate;
        this.jobRepository = jobRepository;
        this.jobStateRepository = jobStateRepository;
        this.jobSuppliedDataRepository = jobSuppliedDataRepository;
        this.jobTypeRepository = jobTypeRepository;
        this.jobDataMediaTypeRepository = jobDataMediaTypeRepository;
        this.jobDataEncodingRepository = jobDataEncodingRepository;
        this.jobSpecificationRepository = jobSpecificationRepository;
        this.jobGeneratedDataRepository = jobGeneratedDataRepository;
    }

    public Optional<Job> tryGetNextAvailableJob() {
        // uses JDBC query here to get the ID because it has to use a skip-locks query.

        List<Long> jobIds = jdbcTemplate.queryForList(SQL_NEXT_AVAILABLE_JOB_ID, Long.class);

        return switch (jobIds.size()) {
            case 0 -> Optional.empty();
            case 1 -> jobRepository.findById(jobIds.getLast());
            default -> throw new  IllegalStateException("more than one next available job");
        };
    }

    public long countNotFinished() {
        return jobRepository.count(JobJpaSpecification.notHasStatus(
                Set.of(JobSnapshot.Status.FINISHED, JobSnapshot.Status.FAILED, JobSnapshot.Status.CANCELLED)
        ));
    }

    public boolean isFinished(String jobCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");
        int count = (int) jobRepository.count(JobJpaSpecification.code(jobCode).and(JobJpaSpecification.hasCompletedStatus()));
        return switch (count) {
            case 0 -> false;
            case 1 -> true;
            default -> throw new IllegalStateException("more than one job found for [" + jobCode + "]");
        };
    }

    public boolean existsjob(String jobCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");
        return jobRepository.existsByCode(jobCode);
    }

    public Optional<Job> tryGetJob(String jobCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");
        return jobRepository.getByCode(jobCode);
    }

    /**
     * <p>Deletes the {@link Job} and any associated objects.</p>
     */
    public boolean deleteJob(String jobCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");

        Optional<Job> jobOptional = jobRepository.getByCode(jobCode);

        if (jobOptional.isEmpty()) {
            return false;
        }

        deleteJob(jobOptional.get());
        return true;
    }

    /**
     * @return true if the percentage was changed.
     */
    public boolean setJobProgressPercent(String jobCode, Integer progressPercent) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(jobCode), "the job code must be supplied");

        if (null != progressPercent && (progressPercent < 0 || progressPercent > 100)) {
            throw new IllegalStateException("bad progress percent [" + progressPercent + "]");
        }

        JobState jpaJobState = jobStateRepository.findByJobCode(jobCode)
                .orElseThrow(() -> new JobServiceException("cannot find job [" + jobCode + "]"));

        Integer existingPercentage = jpaJobState.getProgressPercent();

        switch (mapStateToStatus(jpaJobState)) {
            case FINISHED:
                if (null == existingPercentage || !existingPercentage.equals(progressPercent)) {
                    jpaJobState.setProgressPercent(100);
                    jobStateRepository.save(jpaJobState);
                    return true;
                }
                break;
            case STARTED:
                if (null == existingPercentage || !existingPercentage.equals(progressPercent)) {
                    jpaJobState.setProgressPercent(progressPercent);
                    jobStateRepository.save(jpaJobState);
                    return true;
                }
                break;
        }

        return false;
    }

    public long clearExpiredJobs(Instant now) {
        Preconditions.checkArgument(null != now, "the now instant must be supplied");

        AtomicLong counter = new AtomicLong(0);
        jobRepository.stream(
                        JobJpaSpecification.expiredBeforeTimestamp(now),
                        Job.class,
                        null)
                .forEach(j -> {
                    counter.incrementAndGet();
                    deleteJob(j);
                });
        return counter.get();
    }

    public long clearCompletedExpiredJobs(Instant now) {
        Preconditions.checkArgument(null != now, "the now instant must be supplied");

        AtomicLong counter = new AtomicLong(0);
        jobRepository.stream(
                        JobJpaSpecification.expiredBeforeTimestamp(now).and(JobJpaSpecification.hasCompletedStatus()),
                        Job.class,
                        null)
                .forEach(j -> {
                    counter.incrementAndGet();
                    deleteJob(j);
                });
        return counter.get();
    }

    public List<Job> findJobs(String userNickname, Set<JobSnapshot.Status> statuses, int offset, int limit) {
        Preconditions.checkArgument(offset >= 0, "illegal offset value");

        if (limit < 1) {
            throw new IllegalArgumentException("the limit %d must be > 1".formatted(limit));
        }

        return jobRepository.findAll(
                jobSpecificationForUserNicknameAndStatuses(userNickname, statuses),
                new OffsetBasedPageRequest(limit, offset, Sort.by(Sort.Direction.DESC, Job_.CREATE_TIMESTAMP)));
    }

    public long totalJobs(String userNickname, Set<JobSnapshot.Status> statuses) {
        return jobRepository.count(jobSpecificationForUserNicknameAndStatuses(userNickname, statuses));
    }

    public Optional<JobSuppliedData> tryGetSuppliedJobData(String suppliedJobDataCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(suppliedJobDataCode), "the supplied job data code must be supplied");
        return jobSuppliedDataRepository.findByCode(suppliedJobDataCode);
    }

    public Optional<JobGeneratedData> tryGetGeneratedJobData(String generatedJobDataCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(generatedJobDataCode), "the generated job data code must be supplied");
        return jobGeneratedDataRepository.findByCode(generatedJobDataCode);
    }

    public void ensureJobDataMediaType(String mediaTypeCode) {
        Preconditions.checkArgument(!Strings.isNullOrEmpty(mediaTypeCode), "the media type code must be supplied");
        getOrCreateJpaJobDataMediaType(mediaTypeCode);
    }

    public JobGeneratedData createGeneratedJobData(String jobCode, String jobDataCode, String useCode, String mediaTypeCode, String encodingCode) {

        Preconditions.checkArgument(StringUtils.isNotBlank(jobCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(jobDataCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(mediaTypeCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(encodingCode));

        JobGeneratedData jpaJobData = new JobGeneratedData();
        JobState jobState = jobStateRepository.findByJobCode(jobCode)
                .orElseThrow(() -> new JobServiceException("unable to find job with code [" + jobCode + "]"));

        jpaJobData.setCode(jobDataCode);
        jpaJobData.setStorageCode(jobDataCode);
        jpaJobData.setUseCode(useCode);
        jpaJobData.setJobState(jobState);
        jpaJobData.setMediaType(getOrCreateJpaJobDataMediaType(mediaTypeCode));
        jpaJobData.setEncoding(jobDataEncodingRepository.getByCode(encodingCode));
        jpaJobData = jobGeneratedDataRepository.save(jpaJobData);

        if (null == jobState.getGeneratedDatas()) {
            jobState.setGeneratedDatas(new ArrayList<>());
        }

        jobState.getGeneratedDatas().add(jpaJobData);
        jobStateRepository.save(jobState); // save reverse relationship

        return jpaJobData;
    }

    public JobSuppliedData createSuppliedJobData(String jobDataCode, String useCode, String mediaTypeCode, String encodingCode) {

        Preconditions.checkArgument(StringUtils.isNotBlank(jobDataCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(mediaTypeCode));
        Preconditions.checkArgument(StringUtils.isNotBlank(encodingCode));

        JobSuppliedData jpaJobData = new JobSuppliedData();

        jpaJobData.setCode(jobDataCode);
        jpaJobData.setStorageCode(jobDataCode);
        jpaJobData.setUseCode(useCode);

        jpaJobData.setMediaType(getOrCreateJpaJobDataMediaType(mediaTypeCode));
        jpaJobData.setEncoding(jobDataEncodingRepository.getByCode(encodingCode));

        return jobSuppliedDataRepository.save(jpaJobData);
    }

    public void updateStateStatus(String jobCode, Instant now, JobSnapshot.Status targetStatus) {
        Preconditions.checkArgument(StringUtils.isNotBlank(jobCode));
        Preconditions.checkArgument(null != now, "the now instant must be supplied");
        Preconditions.checkArgument(null != targetStatus, "the targetStatus must be supplied");

        JobState jpaJobState = tryGetJob(jobCode)
                .orElseThrow(() -> new JobServiceException("cannot find job [" + jobCode + "]"))
                .getState();

        JobSnapshot.Status currentStatus = mapStateToStatus(jpaJobState);

        if (currentStatus != targetStatus) {
            switch (targetStatus) {
                case QUEUED:
                    if (currentStatus == JobSnapshot.Status.INDETERMINATE) {
                        jpaJobState.setQueueTimestamp(now);
                        jpaJobState.setProgressPercent(null);
                    } else {
                        throw new JobServiceStateTransitionException(currentStatus, targetStatus);
                    }
                    break;

                case STARTED:
                    if (currentStatus == JobSnapshot.Status.QUEUED) {
                        jpaJobState.setStartTimestamp(now);
                        jpaJobState.setProgressPercent(0);
                    } else {
                        throw new JobServiceStateTransitionException(currentStatus, targetStatus);
                    }
                    break;

                case FINISHED:
                    if (currentStatus == JobSnapshot.Status.STARTED) {
                        jpaJobState.setFinishTimestamp(now);
                        jpaJobState.setProgressPercent(100);
                    } else {
                        throw new JobServiceStateTransitionException(currentStatus, targetStatus);
                    }
                    break;

                case FAILED:
                    jpaJobState.setFailTimestamp(now);
                    break;

                case CANCELLED:
                    jpaJobState.setCancelTimestamp(now);
                    break;

                default:
                    throw new JobServiceStateTransitionException(currentStatus, targetStatus);
            }
        }

        jobStateRepository.save(jpaJobState);
    }

    /**
     * <p>It could be that jobs have started but failed; left in started state. In such a case the `job` row
     * won't be locked but will be started.</p>
     */
    public Set<String> getDanglingStartedJobCodes() {
        return Set.copyOf(jdbcTemplate.query(
                SQL_STARTED_NOT_LOCKED_JOB_CODES,
                (rs, rowNum) -> rs.getString(1)
        ));
    }

    public Stream<Job> streamJobsByTypeAndStatuses(
            String jobTypeCode,
            Set<JobSnapshot.Status> statuses) {
        Preconditions.checkArgument(null != jobTypeCode, "the job type code must be supplied");

        JobType jpaJobType = jobTypeRepository.getByCode(jobTypeCode);

        // it could be that nobody has run a report of this type yet.

        if (null == jpaJobType) {
            return Stream.empty();
        }

        // This would be way more efficient as a SQL statement, but this is quite comprehensible using JPA. The idea
        // is that we want to get the Jobs in a distinct order; the finished ones, the started ones, the queued ones
        // and then anything else.

        List<Stream<Job>> streams = new ArrayList<>();

        if (statuses.contains(JobSnapshot.Status.FINISHED)) {
            streams.add(jobRepository.stream(
                    JobJpaSpecification.type(jobTypeCode).and(JobJpaSpecification.hasStatus(Set.of(JobSnapshot.Status.FINISHED))),
                    Job.class,
                    Sort.by(Sort.Order.desc(String.join(".", Job_.STATE, JobState_.FINISH_TIMESTAMP)), ORDER_ID)
            ));
        }

        if (statuses.contains(JobSnapshot.Status.STARTED)) {
            streams.add(jobRepository.stream(
                    JobJpaSpecification.type(jobTypeCode).and(JobJpaSpecification.hasStatus(Set.of(JobSnapshot.Status.STARTED))),
                    Job.class,
                    Sort.by(Sort.Order.desc(String.join(".", Job_.STATE, JobState_.START_TIMESTAMP)), ORDER_ID)
            ));
        }

        if (statuses.contains(JobSnapshot.Status.QUEUED)) {
            streams.add(jobRepository.stream(
                    JobJpaSpecification.type(jobTypeCode).and(JobJpaSpecification.hasStatus(Set.of(JobSnapshot.Status.QUEUED))),
                    Job.class,
                    Sort.by(Sort.Order.desc(String.join(".", Job_.STATE, JobState_.QUEUE_TIMESTAMP)), ORDER_ID)
            ));
        }

        Set<JobSnapshot.Status> remainingStatuses = SetUtils.difference(
                statuses,
                Set.of(JobSnapshot.Status.FINISHED, JobSnapshot.Status.STARTED, JobSnapshot.Status.QUEUED));

        if (!remainingStatuses.isEmpty()) {
            streams.add(jobRepository.stream(
                    JobJpaSpecification.type(jobTypeCode).and(JobJpaSpecification.hasStatus(remainingStatuses)),
                    Job.class,
                    SORTS_STATE_TIMESTAMPS
            ));
        }

        return streams.stream().flatMap(s -> s);
    }

    public void createJob(
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

        JobState jpaJobState = new JobState();

        if (started) {
            jpaJobState.setStartTimestamp(now);
        }

        jpaJobState.setQueueTimestamp(now);
        jpaJobState = jobStateRepository.save(jpaJobState);

        org.haiku.haikudepotserver.job.jpa.model.JobSpecification jpaJobSpecification
                = new org.haiku.haikudepotserver.job.jpa.model.JobSpecification();
        jpaJobSpecification.setData(specificationSerialized);
        jpaJobSpecification = jobSpecificationRepository.save(jpaJobSpecification);

        org.haiku.haikudepotserver.job.jpa.model.Job jpaJob = new org.haiku.haikudepotserver.job.jpa.model.Job();
        jpaJob.setCode(code);
        jpaJob.setOwnerUserNickname(ownerUserNickname);
        jpaJob.setType(getOrCreateJpaJobType(jobTypeCode));
        jpaJob.setExpiryTimestamp(now.plus(ttlMillis, ChronoUnit.MILLIS));
        jpaJob.setState(jpaJobState);
        jpaJob.setSpecification(jpaJobSpecification);
        jpaJob = jobRepository.save(jpaJob);

        for (String suppliedDataCode : CollectionUtils.emptyIfNull(suppliedDataCodes)) {
            JobSuppliedData jpaJobData = jobSuppliedDataRepository.findByCode(suppliedDataCode)
                    .orElseThrow(() -> new ObjectNotFoundException(JobSuppliedData.class.getSimpleName(), suppliedDataCode));
            jpaJobData.setJob(jpaJob);
            jpaJobData = jobSuppliedDataRepository.save(jpaJobData);

            if (null == jpaJob.getSuppliedDatas()) {
                jpaJob.setSuppliedDatas(new ArrayList<>());
            }
            jpaJob.getSuppliedDatas().add(jpaJobData);
        }
    }

    /**
     * <p>Deletes the {@link Job} and any associated objects.</p>
     */
    public void deleteJob(Job job) {
        Preconditions.checkArgument(null != job, "the job must be supplied");

        JobState jobState = job.getState();

        jobGeneratedDataRepository.deleteAll(jobState.getGeneratedDatas());
        jobGeneratedDataRepository.flush();

        jobSuppliedDataRepository.deleteAll(job.getSuppliedDatas());
        jobSuppliedDataRepository.flush();

        JobSpecification jobSpecification = job.getSpecification();

        jobRepository.delete(job);
        jobRepository.flush();

        jobSpecificationRepository.delete(jobSpecification);
        jobStateRepository.delete(jobState);
    }

    private Specification<Job> jobSpecificationForUserNicknameAndStatuses(String userNickname, Set<JobSnapshot.Status> statuses) {
        List<Specification<Job>> specifications = new ArrayList<>();

        specifications.add(JobJpaSpecification.any());

        if (null != userNickname) {
            specifications.add(JobJpaSpecification.ownerUserNickname(userNickname));
        }

        if (null != statuses) {
            specifications.add(JobJpaSpecification.hasStatus(statuses));
        }

        return specifications
                .stream()
                .reduce(JobJpaSpecification.any(), Specification::and);
    }

    private JobType getOrCreateJpaJobType(String jobTypeCode) {
        String code = jobTypeCode.toLowerCase();
        JobType jobType = jobTypeRepository.getByCode(code);

        if (null == jobType) {
            jobType = new JobType();
            jobType.setCode(code);
            jobType = jobTypeRepository.save(jobType);
        }

        return jobType;
    }

    private JobDataMediaType getOrCreateJpaJobDataMediaType(String jobDataMediaTypeCode) {
        JobDataMediaType jobDataMediaType = jobDataMediaTypeRepository.getByCode(jobDataMediaTypeCode);

        if (null == jobDataMediaType) {
            jobDataMediaType = new JobDataMediaType();
            jobDataMediaType.setCode(jobDataMediaTypeCode);
            jobDataMediaType = jobDataMediaTypeRepository.save(jobDataMediaType);
        }

        return jobDataMediaType;
    }

    private static JobSnapshot.Status mapStateToStatus(JobState jobState) {
        return Jobs.mapTimestampsToStatus(
                jobState.getFailTimestamp(),
                jobState.getCancelTimestamp(),
                jobState.getFinishTimestamp(),
                jobState.getStartTimestamp(),
                jobState.getQueueTimestamp()
        );
    }

}
