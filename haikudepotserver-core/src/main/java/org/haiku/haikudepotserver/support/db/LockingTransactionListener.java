/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.db;

import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.Uninterruptibles;
import org.apache.cayenne.tx.Transaction;
import org.apache.cayenne.tx.TransactionListener;
import org.haiku.haikudepotserver.support.exception.LockAquisitionException;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;

/**
 * <p>Can be used with the Cayenne transaction system to ensure that a lock is taken out
 * across the transaction in order to prevent contention on some process or object.</p>
 */

public class LockingTransactionListener implements TransactionListener {

    private final static String SQL_TRY_ADVISORY_LOCK = "SELECT pg_try_advisory_xact_lock(?)";

    private final static Duration ACQUIRE_ADVISORY_LOCK_RETRY_SLEEP_MILLIS = Duration.ofMillis(500L);

    private final long lockId;

    private final Duration timeout;

    public LockingTransactionListener(long lockId, Duration timeout) {
        this.lockId = lockId;
        this.timeout = Preconditions.checkNotNull(timeout);
    }

    @Override
    public void willCommit(Transaction tx) {
    }

    @Override
    public void willRollback(Transaction tx) {
    }

    /**
     * <p>When a connection is added, we need to take out a lock to prevent other operations from also performing
     * the operation at the same time.</p>
     */

    @Override
    public void willAddConnection(Transaction tx, String connectionName, Connection connection) {
        try {
            connection.setAutoCommit(false);

            if (!tryAcquireAdvisoryLock(connection, lockId, timeout)) {
                tx.setRollbackOnly();
                throw new LockAquisitionException("unable to acquire the lock [" + lockId + "]");
            }
        }
        catch (SQLException se) {
            throw new LockAquisitionException("error acquiring the advisory lock [" + lockId + "]", se);
        }
    }

    public static boolean tryAcquireAdvisoryLock(Connection connection, long id, Duration timeout) throws SQLException {
        Preconditions.checkArgument(null != connection, "the connection is required");
        Preconditions.checkArgument(null != timeout, "the timeout is required");

        if (connection.getAutoCommit()) {
            throw new IllegalStateException("the connection must be transactional");
        }

        long timeoutMillis = System.currentTimeMillis() + timeout.toMillis();

        while (System.currentTimeMillis() < timeoutMillis) {
            try (PreparedStatement statement = connection.prepareStatement(SQL_TRY_ADVISORY_LOCK)) {
                statement.setLong(1, id);
                try (ResultSet resultSet = statement.executeQuery()) {
                    if (!resultSet.next()) {
                        throw new IllegalStateException("expected a row to be returned");
                    }
                    if (resultSet.getBoolean(1)) {
                        return true;
                    }
                }
            }

            Uninterruptibles.sleepUninterruptibly(ACQUIRE_ADVISORY_LOCK_RETRY_SLEEP_MILLIS);
        }

        return false;
    }
}
