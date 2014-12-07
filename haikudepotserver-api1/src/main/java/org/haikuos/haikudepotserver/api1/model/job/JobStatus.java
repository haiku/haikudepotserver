/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.api1.model.job;

/**
 * <P>Note that this is an API enum only.</P>
 */

public enum JobStatus {
    QUEUED,
    STARTED,
    FINISHED,
    FAILED,
    CANCELLED
}
