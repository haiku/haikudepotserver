/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.model;

/**
 * <p>This exception is thrown when problems happen in the job service.</p>
 */

public class JobServiceException extends RuntimeException{

    public JobServiceException(String message) {
        super(message);
    }

    public JobServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
