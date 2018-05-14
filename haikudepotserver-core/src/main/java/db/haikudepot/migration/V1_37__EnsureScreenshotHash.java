/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package db.haikudepot.migration;

import db.haikudepot.support.EnsureScreenshotHash;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class V1_37__EnsureScreenshotHash extends EnsureScreenshotHash {

    protected static final Logger LOGGER = LoggerFactory.getLogger(V1_37__EnsureScreenshotHash.class);

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

    @Override
    public void migrate(Connection connection) throws Exception {

        addColumn(connection);

        LOGGER.info("did add column for screenshot hashes");

        super.migrate(connection);

        if (0 != countScreenshotsWithoutHash(connection)) {
            throw new IllegalStateException("somehow a screenshot remained without a hash at the end of the migration.");
        }

        setColumnNotNull(connection);

        LOGGER.info("did make column for screenshot hashes not-null");
    }

    @Override
    public String createScreenshotCountStatement() {
        return STATEMENT_COUNT;
    }

    @Override
    public String createScreenshotIdStatement() {
        return STATEMENT_FETCH;
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

}
