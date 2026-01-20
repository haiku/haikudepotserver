/*
 * Copyright 2026, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.progress;

/**
 * <p>An interface which describes an object that is able to express its progress
 * through some work.</p>
 */

public interface Progress {

    /**
     * <p>The percentage of the way through the work.</p>
     */

    int percentage();

}
