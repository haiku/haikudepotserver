/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.eventing.model;

public interface NotifyService {

    /**
     * <p>Send an event out to the recipients in a fan-out topology.</p>
     */

    void publishEvent(InterProcessEvent event);

}
