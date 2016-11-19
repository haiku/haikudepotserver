/*
 * Copyright 2013, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

/**
 * <p>This exception is to express that the icon provided is not suitable; either not a PNG or not the right size.</p>
 */

public class BadPkgIconException extends Exception {

    public BadPkgIconException() {
    }

    public BadPkgIconException(String message) {
        super(message);
    }

}
