/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.captcha;

import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import org.haikuos.haikudepotserver.captcha.model.CaptchaRepository;
import org.haikuos.haikudepotserver.support.Closeables;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import javax.sql.DataSource;
import java.sql.*;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * <p>This object stores the captchas in a database for later retrieval.</p>
 */

public class DatabaseCaptchaRepository implements CaptchaRepository {

    protected static Logger logger = LoggerFactory.getLogger(DatabaseCaptchaRepository.class);

    private DataSource dataSource;
    private Long expirySeconds;

    public DataSource getDataSource() {
        return dataSource;
    }

    public void setDataSource(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public Long getExpirySeconds() {
        return expirySeconds;
    }

    public void setExpirySeconds(Long expirySeconds) {
        this.expirySeconds = expirySeconds;
    }

    public void init() {
        purgeExpired();
    }

    public int purgeExpired() {
        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement("DELETE FROM captcha.responses WHERE create_timestamp < ?");
            preparedStatement.setTimestamp(1,new Timestamp(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(expirySeconds)));
            int deleted = preparedStatement.executeUpdate();

            if(0!=deleted) {
                logger.info("did delete {} expired captcha responses",deleted);
            }

            return deleted;
        }
        catch(SQLException se) {
            throw new RuntimeException("unable to purge expired captcha tokens",se);
        }
        finally {
            Closeables.closeQuietly(preparedStatement);
            Closeables.closeQuietly(connection);
        }
    }

    @Override
    public boolean delete(String token) {
        Preconditions.checkState(!Strings.isNullOrEmpty(token));

        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement("DELETE FROM captcha.responses WHERE token=?");
            preparedStatement.setString(1,token);
            int d = preparedStatement.executeUpdate();
            logger.info("did delete captcha token {}",token);
            return 1==d;
        }
        catch(SQLException se) {
            throw new RuntimeException("unable to delete captcha token",se);
        }
        finally {
            Closeables.closeQuietly(preparedStatement);
            Closeables.closeQuietly(connection);
        }
    }

    @Override
    public String get(String token) {
        Preconditions.checkState(!Strings.isNullOrEmpty(token));

        Connection connection = null;
        PreparedStatement preparedStatement = null;
        ResultSet resultSet = null;

        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement("SELECT response FROM captcha.responses WHERE token=? AND create_timestamp > ?");
            preparedStatement.setString(1,token);
            preparedStatement.setTimestamp(2,new Timestamp(System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(expirySeconds)));
            resultSet = preparedStatement.executeQuery();

            if(!resultSet.next()) {
                return null;
            }

            String result = resultSet.getString(1);

            if(resultSet.next()) {
                throw new IllegalStateException("found more than one captcha for "+token);
            }

            return result;
        }
        catch(SQLException se) {
            throw new RuntimeException("unable to verify captcha token",se);
        }
        finally {
            Closeables.closeQuietly(preparedStatement);
            Closeables.closeQuietly(connection);
            Closeables.closeQuietly(resultSet);
        }
    }

    @Override
    public void store(String token, String response) {
        Preconditions.checkState(!Strings.isNullOrEmpty(token));
        Preconditions.checkState(!Strings.isNullOrEmpty(response));

        Connection connection = null;
        PreparedStatement preparedStatement = null;

        try {
            connection = dataSource.getConnection();
            preparedStatement = connection.prepareStatement("INSERT INTO captcha.responses (create_timestamp, token, response) VALUES (?,?,?)");
            preparedStatement.setTimestamp(1,new Timestamp(System.currentTimeMillis()));
            preparedStatement.setString(2,token);
            preparedStatement.setString(3,response);

            if(1!=preparedStatement.executeUpdate()) {
                throw new IllegalStateException("unable to store the captcha token "+token);
            }

            logger.info("stored captcha token {}",token);
        }
        catch(SQLException se) {
            throw new RuntimeException("unable to delete captcha token",se);
        }
        finally {
            Closeables.closeQuietly(preparedStatement);
            Closeables.closeQuietly(connection);
        }
    }

}
