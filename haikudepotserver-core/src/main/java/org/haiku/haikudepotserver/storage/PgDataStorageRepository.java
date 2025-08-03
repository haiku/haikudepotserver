/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage;

import com.google.common.base.Preconditions;
import com.google.common.io.CountingInputStream;
import com.google.common.util.concurrent.AtomicDouble;
import io.micrometer.core.instrument.MeterRegistry;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.metrics.MetricsConstants;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StopWatch;

import javax.sql.DataSource;
import java.io.*;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * <p>Class for performing operations on the data store. Note that each operation here is a transaction; usually
 * the transaction would be driven at a service layer but owing to the unique low-level nature of this class, this
 * is happening at the repository level in this case.</p>
 */
@Component
public class PgDataStorageRepository {

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

    private final static String SQL_DELETE_PARTS_FOR_HEAD_BY_CODE =
            "DELETE FROM datastore.object_part WHERE object_head_id = (SELECT id FROM datastore.object_head WHERE code = ?)";

    private final static String SQL_DELETE_HEAD_BY_CODE =
            "DELETE FROM datastore.object_head WHERE code = ?";

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

    /**
     * <p>This is used for a metric gauge to show the rate of data transfer.</p>
     */
    private final AtomicDouble mbPerSecondTransfer;

    /**
     * <p>This is a lightweight object that couples simple data about the parts of the data.</p>
     */
    public record Part(long id, long length) {
    }

    private final JdbcTemplate jdbcTemplate;

    private final TransactionTemplate transactionTemplate;

    private final Clock clock;

    public PgDataStorageRepository(
            PlatformTransactionManager transactionManager,
            DataSource dataSource,
            MeterRegistry meterRegistry
    ) {
        jdbcTemplate = new JdbcTemplate(dataSource);
        transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        this.mbPerSecondTransfer = new AtomicDouble();
        meterRegistry.gauge(
                MetricsConstants.GUAGE_PG_DATA_STORAGE_MEGABYTE_PER_SECOND_TRANSFER,
                this.mbPerSecondTransfer);

        this.clock = Clock.systemUTC();
    }

    /**
     * <p>Returns a set of the codes for the datas stored.</p>
     */
    Set<String> findHeadCodesTransactionally(Duration olderThanDuration) {
        return transactionTemplate.execute(status -> findHeadCodes(olderThanDuration));
    }

    /**
     * <p>Creates a new blank object head and returns it's ID.</p>
     */
    long createHeadTransactionally(String key) {
        return Optional.ofNullable(transactionTemplate.execute(status -> createHead(key)))
                .orElseThrow(() -> new IllegalStateException("unable to insert the object head"));
    }

    /**
     * <p>Creates an object part on the object identified by the supplied ID. The content of the part is
     * supplied by the provided file.</p>
     */
    void createPartTransactionally(long headId, File file) {
        transactionTemplate.execute(
                status -> {
                    createPart(headId, file);
                    return Boolean.TRUE;
                }
        );
    }

    void deleteHeadAndPartsByCodeTransactionally(String code) {
        Preconditions.checkArgument(StringUtils.isNoneBlank(code));
        transactionTemplate.execute(
                status -> {
                    deleteHeadAndPartsByCode(code);
                    return Boolean.TRUE;
                }
        );
    }

    void deleteHeadsAndPartsTransactionally() {
        transactionTemplate.execute(
                status -> {
                    deleteHeadsAndParts();
                    return Boolean.TRUE;
                }
        );
    }

    Optional<Long> tryGetHeadIdByCodeTransactionally(String code) {
        return transactionTemplate.execute(status -> tryGetHeadIdByCode(code));
    }

    /**
     * <p>Return metadata about the parts associated with a head with the supplied head ID.</p>
     */
    List<Part> findOrderedPartsByHeadIdTransactionally(long headId) {
        return transactionTemplate.execute(status -> findOrderedPartsByHeadId(headId));
    }

    void writePartDataToFileTransactionally(long partId, File file) {
        transactionTemplate.execute(status -> {
            writePartDataToFile(partId, file);
            return Boolean.TRUE;
        });
    }

    private Set<String> findHeadCodes(Duration olderThanDuration) {
        Preconditions.checkNotNull(olderThanDuration);
        return Set.copyOf(jdbcTemplate.query(
                (conn) -> {
                    PreparedStatement preparedStatement = conn.prepareStatement(SQL_HEAD_CODES);
                    preparedStatement.setTimestamp(1, new java.sql.Timestamp(clock.millis() - olderThanDuration.toMillis()));
                    return preparedStatement;
                },
                (ResultSet rs, int rowNum) -> rs.getString(1)
        ));
    }

    /**
     * <p>Creates a new blank object head and returns it's ID.</p>
     */
    private long createHead(String key) {
        java.sql.Timestamp now = new java.sql.Timestamp(clock.millis());
        long objectId = Optional.ofNullable(jdbcTemplate.queryForObject(
                SQL_SELECT_HEAD_NEXTVAL,
                Long.class
        )).orElseThrow(() -> new IllegalStateException("unable to get the next id for object"));

        int updated = jdbcTemplate.update(SQL_INSERT_HEAD, objectId, now, now, key);

        if (0 == updated) {
            throw new IllegalStateException("unepxectedly did not insert the object head");
        }

        return objectId;
    }

    private void createPart(long headId, File file) {
        Preconditions.checkArgument(headId >= 0, "the primary key is required");
        Preconditions.checkArgument(file.exists(), "the file [%s] does not exist".formatted(file.getAbsolutePath()));
        Preconditions.checkArgument(file.length() > 0, "the file [%s] is empty".formatted(file.getAbsolutePath()));

        try (InputStream inputStream = new FileInputStream(file)) {
            if (1 != jdbcTemplate.update(
                    SQL_INSERT_PART,
                    ps -> {
                        ps.setLong(1, headId);
                        ps.setBinaryStream(2, inputStream);
                        ps.setLong(3, file.length());
                    })) {
                throw new IllegalStateException("unable to insert the object part");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("file buffer related issue updating object part", e);
        }

        if (1 != jdbcTemplate.update(SQL_UPDATE_HEAD_LENGTH, file.length(), new java.sql.Timestamp(clock.millis()), headId)) {
            throw new IllegalStateException("unable to update the object length");
        }
    }

    private void deleteHeadAndPartsByCode(String code) {
        Preconditions.checkArgument(StringUtils.isNoneBlank(code));

        // can't really tell how many body parts there might be; 0 --> ?
        jdbcTemplate.update(SQL_DELETE_PARTS_FOR_HEAD_BY_CODE, code);

        if (1 != jdbcTemplate.update(SQL_DELETE_HEAD_BY_CODE, code)) {
            throw new IllegalStateException("unable to delete the object head");
        }
    }

    private void deleteHeadsAndParts() {
        jdbcTemplate.execute(SQL_TRUNCATE_PARTS_AND_HEAD);
    }

    private Optional<Long> tryGetHeadIdByCode(String code) {
        Preconditions.checkArgument(StringUtils.isNotBlank(code), "the code is required");
        List<Long> headIds = jdbcTemplate.query(
                SQL_SELECT_HEAD_ID_BY_CODE,
                ps -> ps.setString(1, code),
                (rs, rowNum) -> rs.getLong(1)
        );
        switch (headIds.size()) {
            case 0: return Optional.empty();
            case 1: return Optional.of(headIds.getLast());
            default: throw new IllegalStateException("more than one head id found for code [" + code + "]");
        }
    }

    private List<Part> findOrderedPartsByHeadId(long headId) {
        Preconditions.checkArgument(headId >= 0, "the head id is required");
        return jdbcTemplate.query(
                SQL_SELECT_PARTS_BY_HEAD_ID,
                ps -> ps.setLong(1, headId),
                (rs, rowNum) -> new Part(rs.getLong(1), rs.getLong(2))
        );
    }

    private double megabytePerSecond(long bytes, double seconds) {
        long kilobytes = bytes / 1024;
        return ((double) kilobytes / seconds) / 1024.0;
    }

    void writePartDataToFile(long partId, File file) {
        jdbcTemplate.query(SQL_SELECT_PART_DATA,
                ps -> {
                    ps.setLong(1, partId);
                },
                rs -> {
                    try (
                            InputStream inputStream = rs.getBinaryStream(1);
                            CountingInputStream countingInputStream = new CountingInputStream(inputStream);
                            FileOutputStream outputStream = new FileOutputStream(file)) {

                        StopWatch stopWatch = new StopWatch();

                        stopWatch.start();
                        countingInputStream.transferTo(outputStream);
                        stopWatch.stop();

                        mbPerSecondTransfer.set(megabytePerSecond(countingInputStream.getCount(), stopWatch.getTotalTimeSeconds()));
                    } catch (IOException ioe) {
                        throw new UncheckedIOException("unable to write part [" + partId + "] to file [" + file + "]", ioe);
                    }
                }
        );
    }

}
