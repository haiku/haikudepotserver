/*
 * Copyright 2023, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.exception;

import java.sql.SQLException;

public class LockAquisitionException extends Error {

    public LockAquisitionException(String message) {
        super(message);
    }

    public LockAquisitionException(String message, SQLException cause) {
        super(cause);
    }

}
