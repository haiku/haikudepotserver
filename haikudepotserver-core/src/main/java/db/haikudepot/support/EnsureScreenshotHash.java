/*
 * Copyright 2018-2022, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package db.haikudepot.support;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.function.Consumer;

/**
 * <p>This migration will ensure that all of the screenshots have hashes stored for them.
 * The same thing could be achieved by using pg-crypto, but this requires database admin
 * changes and would get complicated.</p>
 */

public abstract class EnsureScreenshotHash extends BaseJavaMigration {

    protected static final Logger LOGGER = LoggerFactory.getLogger(EnsureScreenshotHash.class);

    private static final HashFunction HASH_FUNCTION = Hashing.sha256();

    private static final String STATEMENT_UPDATE = "UPDATE haikudepot.pkg_screenshot SET hash_sha256 = ? WHERE id = ?";

    public abstract String createScreenshotCountStatement();

    public abstract String createScreenshotIdStatement();

    @Override
    public void migrate(Context context) throws Exception {
        migrate(context.getConnection());
    }

    protected void migrate(Connection connection) throws Exception {

        int total = countScreenshotsWithoutHash(connection);

        LOGGER.info("did find {} screenshots requiring a hash", total);

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

        LOGGER.info("did complete updating screenshots' hashes.");

    }

    protected int countScreenshotsWithoutHash(Connection connection) throws SQLException {
        try (
                PreparedStatement statement = connection.prepareStatement(createScreenshotCountStatement());
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
                PreparedStatement statement = connection.prepareStatement(createScreenshotIdStatement());
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
