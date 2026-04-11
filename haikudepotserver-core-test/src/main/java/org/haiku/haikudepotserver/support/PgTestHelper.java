/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <p>Utilities related to Postgres database.</p>
 */

public class PgTestHelper {

    private final static String SELECT_NEXTVAL = "SELECT NEXTVAL(?)";

    public static long nextVal(DataSource dataSource, String sequenceName) throws SQLException {
        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(SELECT_NEXTVAL)
        ) {
            connection.setAutoCommit(false);

            statement.setString(1, sequenceName);

            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getLong(1);
                }
            }
            throw new IllegalStateException(String.format("cannot get the next in sequence [%s]", sequenceName));
        }
    }

}
