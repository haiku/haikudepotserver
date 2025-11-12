/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.deployment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessApplicationEvent;

/**
 * <p>This event will cause all instances to be shutdown.</p>
 */

public class ShutdownAllInstancesEvent extends InterProcessApplicationEvent {

    @JsonCreator
    public ShutdownAllInstancesEvent() {
    }

}