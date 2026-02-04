/*
 * Copyright 2025-2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage;

import com.google.common.base.Preconditions;
import com.google.common.io.CountingInputStream;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.storage.model.DataStorageException;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StopWatch;

import java.io.*;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * <p>Class for performing operations on the data store. Note that each operation here is a transaction; usually
 * the transaction would be driven at a service layer but owing to the unique low-level nature of this class, this
 * is happening at the repository level in this case.</p>
 */
@Component
public class PgDataStorageHelper {

    private final static String PLACEHOLDER_HEAD_CODES = "%%HEAD_CODES%%";

    private final static String SQL_SELECT_HEAD_NEXTVAL = "SELECT NEXTVAL('datastore.object_head_seq')";

    private final static String SQL_INSERT_HEAD = """
            INSERT INTO datastore.object_head (id, modify_timestamp, create_timestamp, length, code)
            VALUES (?, ?, ?, 0, ?)
            """;

    private final static String SQL_INSERT_PART = """
            INSERT INTO datastore.object_part (id, object_head_id, data, length, ordering) VALUES (
                NEXTVAL('datastore.object_part_seq'), ?, ?, ?,
                NEXTVAL('datastore.object_part_ordering_seq'))
            """;

    private final static String SQL_UPDATE_HEAD_LENGTH = """
            UPDATE datastore.object_head SET length = length + ?, modify_timestamp = ? WHERE id = ?
            """;

    private final static String SQL_DELETE_PARTS_FOR_HEAD_BY_CODES =
            "DELETE FROM datastore.object_part WHERE object_head_id = (SELECT id FROM datastore.object_head WHERE code IN %%HEAD_CODES%%)";

    private final static String SQL_DELETE_HEAD_BY_CODES =
            "DELETE FROM datastore.object_head WHERE code IN %%HEAD_CODES%%";

    private final static String SQL_TRUNCATE_PARTS_AND_HEAD =
            "TRUNCATE datastore.object_part, datastore.object_head";

    private final static String SQL_SELECT_HEAD_ID_BY_CODE =
            "SELECT oh.id FROM datastore.object_head oh WHERE oh.code = ? LIMIT 2";

    private final static String SQL_SELECT_PARTS_BY_HEAD_ID =
            "SELECT op.id, op.length FROM datastore.object_part op WHERE op.object_head_id = ? ORDER BY op.ordering ASC";

    private final static String SQL_SELECT_PART_DATA =
            "SELECT op.data FROM datastore.object_part op WHERE op.id = ?";

    private final static String SQL_HEAD_CODES =
            "SELECT oh.code FROM datastore.object_head oh WHERE oh.modify_timestamp < ?";

    private final static String SQL_HEAD_COUNT =
            "SELECT COUNT(oh.code) FROM datastore.object_head oh";

    private final static String SQL_HEAD_LENGTH_SUM =
            "SELECT SUM(oh.length) FROM datastore.object_head oh";

    /**
     * <p>This is a lightweight object that couples simple data about the parts of the data.</p>
     */
    public record Part(long id, long length) {
    }

    public record WriteDataPartStats(double megabytesPerSecond) {
    }

    static long getHeadCount(Connection connection) throws SQLException {
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(SQL_HEAD_COUNT);
                ResultSet resultSet = preparedStatement.executeQuery()
        ) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    static long getHeadLengthSum(Connection connection) throws SQLException {
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(SQL_HEAD_LENGTH_SUM);
                ResultSet resultSet = preparedStatement.executeQuery()
        ) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    /**
     * <p>Returns a set of the codes for the datas stored.</p>
     */
    static Set<String> findHeadCodes(Connection connection, Clock clock, Duration olderThanDuration) throws SQLException {
        Preconditions.checkNotNull(olderThanDuration);
        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_HEAD_CODES)) {

            preparedStatement.setTimestamp(1, new java.sql.Timestamp(clock.millis() - olderThanDuration.toMillis()));
            Set<String> result = new HashSet<>();

            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(resultSet.getString(1));
                }
            }

            return Collections.unmodifiableSet(result);
        }
    }

    /**
     * <p>Creates a new blank object head and returns it's ID.</p>
     */
    static long createHead(Connection connection, Clock clock, String key) throws SQLException {
        Preconditions.checkNotNull(connection);
        Preconditions.checkNotNull(key);

        java.sql.Timestamp now = new java.sql.Timestamp(clock.millis());
        long objectId = getNextHeadId(connection);

        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT_HEAD)) {
            preparedStatement.setLong(1, objectId);
            preparedStatement.setTimestamp(2, now);
            preparedStatement.setTimestamp(3, now);
            preparedStatement.setString(4, key);

            if (1 != preparedStatement.executeUpdate()) {
                throw new DataStorageException("unexpectedly did not insert the object head");
            }
        }

        return objectId;
    }

    /**
     * <p>Creates an object part on the object identified by the supplied ID. The content of the part is
     * supplied by the provided file.</p>
     */
    static void createPart(Connection connection, Clock clock, long headId, File file) throws SQLException {
        Preconditions.checkArgument(headId >= 0, "the primary key is required");
        Preconditions.checkArgument(file.exists(), "the file [%s] does not exist".formatted(file.getAbsolutePath()));
        Preconditions.checkArgument(file.length() > 0, "the file [%s] is empty".formatted(file.getAbsolutePath()));

        try (InputStream inputStream = new FileInputStream(file)) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_INSERT_PART)) {
                preparedStatement.setLong(1, headId);
                preparedStatement.setBinaryStream(2, inputStream);
                preparedStatement.setLong(3, file.length());

                if (1 != preparedStatement.executeUpdate()) {
                    throw new IllegalStateException("unable to insert the object part");
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("file buffer related issue updating object part", e);
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_UPDATE_HEAD_LENGTH)) {
            preparedStatement.setLong(1, file.length());
            preparedStatement.setTimestamp(2, new java.sql.Timestamp(clock.millis()));
            preparedStatement.setLong(3, headId);

            if (1 != preparedStatement.executeUpdate()) {
                throw new DataStorageException("unable to update the object length");
            }
        }
    }

    static int deleteHeadAndPartsByCodes(Connection connection, Set<String> codes) throws SQLException {
        if (CollectionUtils.isEmpty(codes)) {
            return 0;
        }

        List<String> codesSorted = codes.stream().sorted().toList(); // deterministic
        String headCodePlaceholders = "(" + Stream.generate(() -> "?").limit(codes.size()).collect(Collectors.joining(",")) + ")";
        String sqlDeletePartsForHeadByCodes = SQL_DELETE_PARTS_FOR_HEAD_BY_CODES.replace(PLACEHOLDER_HEAD_CODES, headCodePlaceholders);
        String sqlDeleteHeadByCodes = SQL_DELETE_HEAD_BY_CODES.replace(PLACEHOLDER_HEAD_CODES, headCodePlaceholders);

        // can't really tell how many body parts there might be; 0 --> ?

        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlDeletePartsForHeadByCodes)) {
            for (int i = 0; i < codesSorted.size(); i++) {
                preparedStatement.setString(i + 1, codesSorted.get(i));
            }
            preparedStatement.executeUpdate();
        }

        try (PreparedStatement preparedStatement = connection.prepareStatement(sqlDeleteHeadByCodes)) {
            for (int i = 0; i < codesSorted.size(); i++) {
                preparedStatement.setString(i + 1, codesSorted.get(i));
            }
            return preparedStatement.executeUpdate();
        }
    }

    static void deleteHeadsAndParts(Connection connection) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_TRUNCATE_PARTS_AND_HEAD)) {
            preparedStatement.executeUpdate();
        }
    }

    static Optional<Long> tryGetHeadIdByCode(Connection connection, String code) throws SQLException {
        Preconditions.checkArgument(StringUtils.isNotBlank(code), "the code is required");

        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_SELECT_HEAD_ID_BY_CODE)) {
            preparedStatement.setString(1, code);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                return resultSet.next() ? Optional.of(resultSet.getLong(1)) : Optional.empty();
            }
        }
    }

    /**
     * <p>Return metadata about the parts associated with a head with the supplied head ID.</p>
     */
    static List<Part> findOrderedPartsByHeadId(Connection connection, long headId) throws SQLException {
        Preconditions.checkArgument(headId >= 0, "the head id is required");
        List<Part> result = new ArrayList<>();

        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_SELECT_PARTS_BY_HEAD_ID)) {
            preparedStatement.setLong(1, headId);
            try (ResultSet resultSet = preparedStatement.executeQuery()) {
                while (resultSet.next()) {
                    result.add(new Part(resultSet.getLong(1), resultSet.getLong(2)));
                }
            }
        }

        return Collections.unmodifiableList(result);
    }

    static private double megabytePerSecond(long bytes, double seconds) {
        long kilobytes = bytes / 1024;
        return ((double) kilobytes / seconds) / 1024.0;
    }

    static WriteDataPartStats writePartDataToFile(Connection connection, long partId, File file) throws SQLException {
        Preconditions.checkArgument(null != connection, "the connection is required");
        Preconditions.checkArgument(null != file, "the file is required");
        double megabytePerSecond;

        try (PreparedStatement preparedStatement = connection.prepareStatement(SQL_SELECT_PART_DATA)) {
            preparedStatement.setLong(1, partId);

            try (ResultSet resultSet = preparedStatement.executeQuery()) {

                if (!resultSet.next()) {
                    throw new DataStorageException("unable to find the data part [%d]".formatted(partId));
                }

                try (
                        InputStream inputStream = resultSet.getBinaryStream(1);
                        CountingInputStream countingInputStream = new CountingInputStream(inputStream);
                        FileOutputStream outputStream = new FileOutputStream(file)) {

                    StopWatch stopWatch = new StopWatch();

                    stopWatch.start();
                    countingInputStream.transferTo(outputStream);
                    stopWatch.stop();

                    megabytePerSecond = megabytePerSecond(countingInputStream.getCount(), stopWatch.getTotalTimeSeconds());
                } catch (IOException ioe) {
                    throw new UncheckedIOException("unable to write part [" + partId + "] to file [" + file + "]", ioe);
                }
            }
        }

        return new WriteDataPartStats(megabytePerSecond);
    }

    static private long getNextHeadId(Connection connection) throws SQLException {
        try (
                PreparedStatement preparedStatement = connection.prepareStatement(SQL_SELECT_HEAD_NEXTVAL);
                ResultSet resultSet = preparedStatement.executeQuery()
        ) {
            if (!resultSet.next()) {
                throw new DataStorageException("unable to get a new id for the head");
            }

            return resultSet.getLong(1);
        }
    }

}
