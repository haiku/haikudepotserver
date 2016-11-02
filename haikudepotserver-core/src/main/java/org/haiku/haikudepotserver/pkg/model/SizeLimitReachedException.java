/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.pkg.model;

import java.io.IOException;

/**
 * <p>Thrown when more bytes are read from an input than were anticipated.</p>
 */

public class SizeLimitReachedException extends IOException {

    public SizeLimitReachedException() {
    }

}
