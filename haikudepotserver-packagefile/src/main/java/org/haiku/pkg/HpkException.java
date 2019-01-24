/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

/**
 * <p>This type of exception is used through the Hpk file processing system to indicate that something has gone wrong
 * with processing the Hpk data in some way.</p>
 */

public class HpkException extends Error {

    public HpkException(String message) {
        super(message);
    }

    public HpkException(String message, Throwable cause) {
        super(message, cause);
    }

}
