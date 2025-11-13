package org.haiku.haikudepotserver.support.db;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Uninterruptibles;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

/**
 * <p>This class supports the application taking out Postgres advisory locks.</p>
 */

public class PgAdvisoryLockHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PgAdvisoryLockHelper.class);

    private final static String SQL_TRY_LOCK = "SELECT pg_try_advisory_xact_lock(?)";
    private final static String SQL_TRY_LOCK_SHARED = "SELECT pg_try_advisory_xact_lock_shared(?)";
    private final static Duration DELAY_RETRY = Duration.ofSeconds(2);

    /**
     * <p>This will attempt to take out the lock in the connection. It will try for the specified duration if the
     * `duration` parameter is supplied.</p>
     * @param key identifies the lock
     * @param timeout specifies how long to try to acquire the lock for
     * @param shared take out a shared lock instead of an exclusive lock
     * @return true if the lock was taken out.
     */

    public static boolean tryTransactionalAdvisoryLock(
            Connection connection,
            long key,
            boolean shared,
            Duration timeout
    ) throws SQLException {
        Preconditions.checkArgument(null != connection);
        Preconditions.checkArgument(key > 0);
        Preconditions.checkArgument(null != timeout);

        if (timeout.isNegative()) {
            return false;
        }

        Instant start = Instant.now();

        while (Duration.between(start, Instant.now()).compareTo(timeout) < 0) {
            String sql = shared ? SQL_TRY_LOCK_SHARED : SQL_TRY_LOCK;
            if (runTryAdvistoryLockSql(connection, sql, key)) {
                return true;
            }
            LOGGER.trace("unable to aquire lock; will sleep and try again");
            Uninterruptibles.sleepUninterruptibly(DELAY_RETRY);
        }

        return false;
    }

    private static boolean runTryAdvistoryLockSql(Connection connection, String query, long key) throws SQLException {
        try (PreparedStatement preparedStatement = connection.prepareStatement(query)) {
            preparedStatement.setLong(1, key);
            try (ResultSet rs = preparedStatement.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean(1);
                } else {
                    throw new IllegalStateException("the query [%s] for key [%d] should return a row".formatted(query, key));
                }
            }
        }
    }

}
