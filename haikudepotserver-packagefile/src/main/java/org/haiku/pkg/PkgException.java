/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.pkg;

public class PkgException extends Exception {

    public PkgException(String message) {
        super(message);
    }

    public PkgException(String message, Throwable cause) {
        super(message, cause);
    }

}
