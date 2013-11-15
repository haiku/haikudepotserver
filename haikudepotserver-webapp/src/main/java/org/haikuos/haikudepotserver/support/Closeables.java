/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.support;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * <p>Quite often in finally clauses you need to close off some resource such as a database
 * {@link java.sql.Connection} or an {@link java.io.InputStream} or some such thing.  This
 * class contains some helpers for doing this without throwing more exceptions.</p>
 */

public class Closeables {

    public static void closeQuietly(Closeable closeable) {
        if(null!=closeable) {
            try {
                closeable.close();
            }
            catch(IOException ioe) {
                // ignore.
            }
        }
    }

    public static void closeQuietly(Connection connection) {
        if(null!=connection) {
            try {
                connection.close();
            }
            catch(SQLException e) {
                // ignore
            }
        }
    }

    public static void closeQuietly(PreparedStatement preparedStatement) {
        if(null!=preparedStatement) {
            try {
                preparedStatement.close();
            }
            catch(SQLException e) {
                // ignore
            }
        }
    }

    public static void closeQuietly(ResultSet resultSet) {
        if(null!=resultSet) {
            try {
                resultSet.close();
            }
            catch(SQLException e) {
                // ignore
            }
        }
    }

}
