/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.job.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import org.haiku.haikudepotserver.support.eventing.model.InterProcessEvent;

/**
 * <p>This is an event to indicate that there is work rady to be performed in the job
 * queue.</p>
 */

public class JobAvailableEvent extends InterProcessEvent {

    @JsonCreator
    public JobAvailableEvent() {
    }

}
