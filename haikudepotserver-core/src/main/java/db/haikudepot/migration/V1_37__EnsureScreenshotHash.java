/*
 * Copyright 2017, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package db.haikudepot.migration;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.function.Consumer;

/**
 * <p>This migration will ensure that all of the screenshots have hashes stored for them.
 * The same thing could be achieved by using pg-crypto, but this requires database admin
 * changes and would get complicated.</p>
 */

public class V1_37__EnsureScreenshotHash implements JdbcMigration {

    protected static final Logger LOGGER = LoggerFactory.getLogger(V1_37__EnsureScreenshotHash.class);

    private static final HashFunction HASH_FUNCTION = Hashing.sha256();

    private static final String STATEMENT_DDL_ADD_COLUMN = "ALTER TABLE haikudepot.pkg_screenshot "
        + "ADD COLUMN hash_sha256 VARCHAR (64);";

    private static final String STATEMENT_DDL_NOT_NULL_COLUMN = "ALTER TABLE haikudepot.pkg_screenshot "
            + "ALTER COLUMN hash_sha256 SET NOT NULL";

    private static final String STATEMENT_COUNT = "SELECT COUNT(*) FROM haikudepot.pkg_screenshot "
        + " WHERE hash_sha256 IS NULL";

    private static final String STATEMENT_FETCH = "SELECT ps.id AS pkg_screenshot_id, "
        + "psi.data AS pkg_screenshot_image_data FROM haikudepot.pkg_screenshot ps "
            + "JOIN haikudepot.pkg_screenshot_image psi ON psi.pkg_screenshot_id = ps.id "
            + " WHERE ps.hash_sha256 IS NULL";

    private static final String STATEMENT_UPDATE = "UPDATE haikudepot.pkg_screenshot SET hash_sha256 = ? WHERE id = ?";

    @Override
    public void migrate(Connection connection) throws Exception {

        addColumn(connection);

        LOGGER.info("did add column for screenshot hashes");

        int total = countScreenshotsWithoutHash(connection);

        LOGGER.info("did find {} screenshots stored without a hash", total);

        forEachScreenshotWithoutHash(total, connection, swh -> {
            try {
                updateScreenshotHash(
                        connection,
                        swh.getPkgScreenshotId(),
                        HASH_FUNCTION.hashBytes(swh.getImageData()).toString());
            } catch (SQLException se) {
                throw new IllegalStateException("unable to update a screenshot", se);
            }
        });

        LOGGER.info("did complete updating screenshots so they all have hashes.");

        if (0 != countScreenshotsWithoutHash(connection)) {
            throw new IllegalStateException("somehow a screenshot remained without a hash at the end of the migration.");
        }

        setColumnNotNull(connection);

        LOGGER.info("did make column for screenshot hashes not-null");
    }

    private void addColumn(Connection connection) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(STATEMENT_DDL_ADD_COLUMN);
        ) {
            statement.execute();
        }
    }

    private void setColumnNotNull(Connection connection) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(STATEMENT_DDL_NOT_NULL_COLUMN);
        ) {
            statement.execute();
        }
    }

    private int countScreenshotsWithoutHash(Connection connection) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(STATEMENT_COUNT);
                ResultSet resultSet = statement.executeQuery()
        ) {
            if (resultSet.next()) {
                return resultSet.getInt(1);
            }
        }

        return 0;
    }

    private void updateScreenshotHash(Connection connection, Long id, String value) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(STATEMENT_UPDATE);
        ) {
            statement.setString(1, value);
            statement.setLong(2, id);

            if (1 != statement.executeUpdate()) {
                throw new IllegalStateException("unable to update the screenshot hash for [" + id + "]");
            }
        }
    }

    private void forEachScreenshotWithoutHash(
            int total,
            Connection connection,
            Consumer<ScreenshotWithoutHash> consumer) throws SQLException {
        int lastPercentage = 0;
        int count = 0;

        try (
                PreparedStatement statement = connection.prepareStatement(STATEMENT_FETCH);
                ResultSet resultSet = statement.executeQuery()
        ) {
            while (resultSet.next()) {
                consumer.accept(new ScreenshotWithoutHash(
                        resultSet.getLong("pkg_screenshot_id"),
                        resultSet.getBytes("pkg_screenshot_image_data")
                ));

                count++;
                int newPercentage = (count * 100) / total;

                if (newPercentage != lastPercentage) {
                    LOGGER.info("processing at {}%", newPercentage);
                    lastPercentage = newPercentage;
                }
            }
        }
    }

    public static class ScreenshotWithoutHash {

        private final Long pkgScreenshotId;

        private final byte[] imageData;

        ScreenshotWithoutHash(Long pkgScreenshotId, byte[] imageData) {
            this.pkgScreenshotId = pkgScreenshotId;
            this.imageData = imageData;
        }

        Long getPkgScreenshotId() {
            return pkgScreenshotId;
        }

        byte[] getImageData() {
            return imageData;
        }
    }

}
