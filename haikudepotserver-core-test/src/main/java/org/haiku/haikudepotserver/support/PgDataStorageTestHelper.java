/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Date;
import java.util.List;

public class PgDataStorageTestHelper {

    /**
     * <p>This will write data to database under a code so that the read operations can be performed against it.</p>
     */

    public static void setupDatas(DataSource dataSource, Date timestamp, String code, List<byte[]> datas) throws SQLException {
        String insertHeadSql = """
            INSERT INTO datastore.object_head (id, modify_timestamp, create_timestamp, length, code)
            VALUES (NEXTVAL('datastore.object_head_seq'), ?, ?, ?, ?)
                """;
        String insertBodySql = """
                INSERT INTO datastore.object_part (id, object_head_id, data, length, ordering)
                VALUES (
                    NEXTVAL('datastore.object_part_seq'),
                    (SELECT id FROM datastore.object_head oh2 WHERE oh2.code = ?),
                    ?, ?,
                    NEXTVAL('datastore.object_part_ordering_seq')
                    )
                """;
        java.sql.Timestamp sqlTimestamp = new java.sql.Timestamp(timestamp.getTime());

        try (Connection connection = dataSource.getConnection()) {
            try (PreparedStatement preparedStatement = connection.prepareStatement(insertHeadSql)) {
                preparedStatement.setTimestamp(1, sqlTimestamp);
                preparedStatement.setTimestamp(2, sqlTimestamp);
                preparedStatement.setInt(3, datas.stream().mapToInt(d -> d.length).sum());
                preparedStatement.setString(4, code);
                preparedStatement.executeUpdate();
            }

            for (byte[] data : datas) {
                try (PreparedStatement preparedStatement = connection.prepareStatement(insertBodySql)) {
                    preparedStatement.setString(1, code);
                    preparedStatement.setBytes(2, data);
                    preparedStatement.setInt(3, data.length);
                    preparedStatement.executeUpdate();
                }
            }
        }
    }

}
