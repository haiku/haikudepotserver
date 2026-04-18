/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job;

import org.haiku.haikudepotserver.job.model.JobSnapshot;
import org.haiku.haikudepotserver.support.PgTestHelper;

import javax.sql.DataSource;
import java.sql.*;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;

public class PgJobTestHelper {

    private final static String SELECT_JOB_TYPE = "SELECT jt.id FROM job2.job_type jt WHERE jt.code = ?";
    private final static String SELECT_JOB_DATA_MEDIA_TYPE = "SELECT jdmt.id FROM job2.job_data_media_type jdmt WHERE jdmt.code = ?";

    private final static String SELECT_TAGS = """
            SELECT jt.code, jt.value FROM job2.job_tag jt JOIN job2.job j ON j.id = jt.job_id WHERE j.code = ?""";

    private final static String SELECT_SUPPLIED_JOB_DATA = """
            SELECT jd.code FROM job2.job_data jd JOIN job2.job_data_type jdt ON jdt.id = jd.job_data_type_id
                           WHERE jdt.code = 'supplied' AND jd.code = ?
            """;

    private final static String SELECT_JOB_STATUS_DATA = """
            SELECT
                j.progress_percent,
                j.fail_timestamp,
                j.cancel_timestamp,
                j.finish_timestamp,
                j.start_timestamp,
                j.queue_timestamp
            FROM job2.job j
            WHERE j.code = ?
            """;

    private final static String SELECT_JOB_DATA_CODES_FOR_JOB = """
            SELECT jd.code
            FROM job2.job_data jd
            JOIN job2.job j ON j.id = jd.job_id
            JOIN job2.job_data_type jdt ON jdt.id = jd.job_data_type_id
            WHERE 1 = 1 AND j.code = ? AND jdt.code = ?
            """;

    private final static String INSERT_JOB_TYPE = "INSERT INTO job2.job_type (id, code) VALUES (?, ?)";
    private final static String INSERT_JOB_DATA_MEDIA_TYPE = "INSERT INTO job2.job_data_media_type (id, code) VALUES (?, ?)";

    private final static String INSERT_JOB_DATA = """
            INSERT INTO job2.job_data (
                id,
                job_id,
                modify_timestamp,
                create_timestamp,
                code,
                use_code,
                storage_code,
                job_data_encoding_id,
                job_data_media_type_id,
                job_data_type_id
            ) VALUES (
                ?,
                (SELECT j2.id FROM job2.job j2 WHERE j2.code = ?),
                ?,
                ?,
                ?,
                ?,
                ?,
                (SELECT jda.id FROM job2.job_data_encoding jda WHERE jda.code = ?),
                ?,
                (SELECT jdt.id FROM job2.job_data_type jdt WHERE jdt.code = ?)
            )
            """;

    private final static String INSERT_JOB_ASSIGNMENT = """
            INSERT INTO job2.job_assignment (id, code) VALUES (?, ?)
            """;

    private final static String INSERT_JOB = """
            INSERT INTO job2.job (
                id,
                modify_timestamp,
                create_timestamp,
                start_timestamp,
                finish_timestamp,
                queue_timestamp,
                fail_timestamp,
                cancel_timestamp,
                progress_percent,
                code,
                job_type_id,
                specification,
                expiry_timestamp,
                owner_user_nickname,
                job_assignment_id
            ) VALUES (
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                ?,
                (SELECT jt.id FROM job2.job_type jt WHERE jt.code = ?),
                ?,
                ?,
                ?,
                ?
            );
            """;

    public static Optional<TestJobState> tryGetTestJobStateForJob(DataSource dataSource, String jobCode) throws SQLException {
        Function<Timestamp, Instant> mapTimestampFn = (ts) -> Optional.ofNullable(ts)
                .map(ts1 -> Instant.ofEpochMilli(ts1.getTime()))
                .orElse(null);

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_JOB_STATUS_DATA)
        ) {
            connection.setAutoCommit(false);

            statement.setString(1, jobCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    return Optional.empty();
                }

                return Optional.of(
                        new TestJobState(
                                Jobs.mapTimestampsToStatus(
                                        mapTimestampFn.apply(resultSet.getTimestamp(2)), // fail_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(3)), // cancel_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(4)), // finish_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(5)), // start_timestamp
                                        mapTimestampFn.apply(resultSet.getTimestamp(6)) // queue_timestamp
                                ),
                                resultSet.getInt(1)
                        ));
            }
        }
    }

    private static long getOrCreateJobType(DataSource dataSource, String jobTypeCode) throws SQLException {
        Optional<Long> idOptional = getEnumTableEntry(dataSource, SELECT_JOB_TYPE, jobTypeCode);

        if (idOptional.isEmpty()) {
            return createEnumTableEntry(
                    dataSource,
                    INSERT_JOB_TYPE,
                    "job2.job_type_seq",
                    jobTypeCode);
        }

        return idOptional.get();
    }

    public static long getOrCreateJobDataMediaType(DataSource dataSource, String jobDataMediaTypeCode) throws SQLException {
        Optional<Long> idOptional = getEnumTableEntry(dataSource, SELECT_JOB_DATA_MEDIA_TYPE, jobDataMediaTypeCode);

        if (idOptional.isEmpty()) {
            return createEnumTableEntry(
                    dataSource,
                    INSERT_JOB_DATA_MEDIA_TYPE,
                    "job2.job_data_media_type_seq",
                    jobDataMediaTypeCode);
        }

        return idOptional.get();
    }

    private static Optional<Long> getEnumTableEntry(DataSource dataSource, String selectSql, String code) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(selectSql)
        ) {
            connection.setAutoCommit(false);

            statement.setString(1, code);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(resultSet.getLong(1));
                }
            }
        }
        return Optional.empty();
    }

    private static long createEnumTableEntry(
            DataSource dataSource,
            String insertSql,
            String sequenceName,
            String code
    ) throws SQLException {
        long id =  PgTestHelper.nextVal(dataSource, sequenceName);

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(insertSql)
        ) {
            connection.setAutoCommit(false);

            statement.setLong(1, id);
            statement.setString(2, code);

            if (1 != statement.executeUpdate()) {
                throw new IllegalStateException(String.format("unable to insert [%s]", code));
            }

            connection.commit();
        }

        return id;
    }

    public static boolean hasJobSuppliedData(DataSource dataSource, String jobDataCode) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_SUPPLIED_JOB_DATA)
        ) {
            connection.setAutoCommit(false);
            statement.setString(1, jobDataCode);

            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    public static String createJobSuppliedData(DataSource dataSource, String jobCode) throws SQLException {
        String jobDataCode = UUID.randomUUID().toString();
        java.sql.Timestamp timestamp = new java.sql.Timestamp(Clock.systemUTC().millis());

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(INSERT_JOB_DATA)
        ) {
            connection.setAutoCommit(false);

            statement.setLong(1, PgTestHelper.nextVal(dataSource, "job2.job_data_seq"));
            statement.setString(2, jobCode);
            statement.setTimestamp(3, timestamp);
            statement.setTimestamp(4, timestamp);
            statement.setString(5, jobDataCode);
            statement.setString(6, "dunno");
            statement.setString(7, jobDataCode);
            statement.setString(8, "none");
            statement.setLong(9, getOrCreateJobDataMediaType(dataSource, "application/octet-stream"));
            statement.setString(10, "supplied");

            if (1 != statement.executeUpdate()) {
                throw new IllegalStateException("failed to insert job data");
            }

            connection.commit();
        }

        return jobDataCode;
    }

    public static String createAnyJob(DataSource dataSource, String jobTypeCode, JobSnapshot.Status jobStatus, Instant now, Instant queueTimestamp) throws SQLException {
        getOrCreateJobType(dataSource, jobTypeCode);

        long jobAssignmentId = PgTestHelper.nextVal(dataSource, "job2.job_assignment_seq");
        long jobId = PgTestHelper.nextVal(dataSource, "job2.job_seq");
        java.sql.Timestamp timestamp = new java.sql.Timestamp(now.toEpochMilli());
        java.sql.Timestamp expiryTimestamp = new java.sql.Timestamp(now.plus(Duration.ofMinutes(30)).toEpochMilli());
        String jobCode = UUID.randomUUID().toString();

        Instant failTimestamp = null;
        Instant cancelTimestamp = null;
        Instant finishTimestamp = null;
        Instant startTimestamp = null;

        switch (jobStatus) {
            case QUEUED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                break;
            case STARTED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                startTimestamp = now;
                break;
            case FINISHED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                startTimestamp = now;
                finishTimestamp = now;
                break;
            case FAILED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                startTimestamp = now;
                failTimestamp = now;
                break;
            case CANCELLED:
                if (null == queueTimestamp) {
                    queueTimestamp = now;
                }
                cancelTimestamp = now;
                break;
            case INDETERMINATE:
                break;
        }

        Function<Instant, java.sql.Timestamp> toTimestampFn = (i) ->
                Optional.ofNullable(i).map(Instant::toEpochMilli).map(java.sql.Timestamp::new).orElse(null);

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statementJobAssignment = connection.prepareStatement(INSERT_JOB_ASSIGNMENT);
                PreparedStatement statementJob = connection.prepareStatement(INSERT_JOB)
        ) {
            connection.setAutoCommit(false);

            statementJobAssignment.setLong(1, jobAssignmentId);
            statementJobAssignment.setString(2, jobCode);

            if (1 != statementJobAssignment.executeUpdate()) {
                throw new IllegalStateException("failed to insert job assignment");
            }

            statementJob.setLong(1, jobId);
            statementJob.setTimestamp(2, timestamp); // modify_timestamp
            statementJob.setTimestamp(3, timestamp); // create_timestamp
            statementJob.setTimestamp(4, toTimestampFn.apply(startTimestamp)); // start_timestamp
            statementJob.setTimestamp(5, toTimestampFn.apply(finishTimestamp)); // finish_timestamp
            statementJob.setTimestamp(6, toTimestampFn.apply(queueTimestamp)); // queue_timestamp
            statementJob.setTimestamp(7, toTimestampFn.apply(failTimestamp)); // fail_timestamp
            statementJob.setTimestamp(8, toTimestampFn.apply(cancelTimestamp)); // cancel_timestamp
            statementJob.setInt(9, 50);
            statementJob.setString(10, jobCode);
            statementJob.setString(11, jobTypeCode);
            statementJob.setString(12, "{}");
            statementJob.setTimestamp(13, expiryTimestamp);
            statementJob.setString(14, "erik");
            statementJob.setLong(15, jobAssignmentId);

            if (1 != statementJob.executeUpdate()) {
                throw new IllegalStateException("failed to insert job");
            }

            connection.commit();
        }

        return jobCode;
    }

    public static Map<String, String> getJobTagsForJobCode(DataSource dataSource, String jobCode) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(SELECT_TAGS)
        ) {
            connection.setAutoCommit(false);

            Map<String, String> result = new HashMap<>();

            preparedStatement.setString(1, jobCode);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while(resultSet.next()) {
                    result.put(
                            resultSet.getString(1),
                            resultSet.getString(2)
                    );
                }
            }

            return Collections.unmodifiableMap(result);
        }
    }

    public static Set<String> getJobDataCodesForJobCode(DataSource dataSource, String jobCode, String jobDataTypeCode) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement preparedStatement = connection.prepareStatement(SELECT_JOB_DATA_CODES_FOR_JOB)
        ) {
            connection.setAutoCommit(false);

            Set<String> result = new HashSet<>();

            preparedStatement.setString(1, jobCode);
            preparedStatement.setString(2, jobDataTypeCode);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while(resultSet.next()) {
                    result.add(resultSet.getString(1));
                }
            }

            return Collections.unmodifiableSet(result);
        }
    }

    public record TestJobState (JobSnapshot.Status status, Integer progressPercentage) {}

}
