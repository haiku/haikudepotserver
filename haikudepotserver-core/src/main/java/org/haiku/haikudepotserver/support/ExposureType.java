/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.support;

/**
 * <P>The exposure defines if the aspect of something is internal-facing
 * or external-facing.  Internal means that the thing is just considered
 * for use in the deployment network and external means for anybody in the
 * outside world to see.</P>
 */

public enum ExposureType {
    INTERNAL_FACING,
    EXTERNAL_FACING
}
