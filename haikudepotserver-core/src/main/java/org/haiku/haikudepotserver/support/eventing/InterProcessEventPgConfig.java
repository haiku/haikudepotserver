/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.eventing;

import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class InterProcessEventPgConfig {

    /**
     * <p>This identifier is unique to an instance of HDS so that it is possible to see if messages
     * that have been received have in fact originated from "self".</p>
     */

    private final String sourceIdentifier = UUID.randomUUID().toString();

    public String getSourceIdentifier() {
        return sourceIdentifier;
    }
}
