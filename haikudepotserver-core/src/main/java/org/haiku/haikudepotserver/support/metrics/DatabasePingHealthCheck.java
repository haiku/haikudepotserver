/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support.metrics;

import com.codahale.metrics.health.HealthCheck;

import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.HashSet;
import java.util.Set;

/**
 * <p>This health check will check to see if the database is accessible by
 * executing a simple query.</p>
 */

public class DatabasePingHealthCheck extends HealthCheck {

    private final static String STATEMENT = "SELECT pc.code FROM haikudepot.pkg_category pc";

    private DataSource dataSource;

    public DatabasePingHealthCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    protected Result check() throws Exception {

        Set<String> categories = new HashSet<>();

        try (
                Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(STATEMENT);
                ResultSet resultSet = statement.executeQuery()) {

            while (resultSet.next()) {
                categories.add(resultSet.getString(1));
            }

        }

        if (categories.isEmpty()) {
            return Result.unhealthy("unable to fetch the package categories to check that the database is functional.");
        }

        return Result.healthy();
    }

}
