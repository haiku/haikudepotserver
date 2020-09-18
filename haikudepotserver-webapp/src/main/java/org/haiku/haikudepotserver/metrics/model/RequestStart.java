/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.metrics.model;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;

import java.time.Instant;

/**
 * <p>This is a model object that holds information about a specific request.</p>
 */

public class RequestStart {

    private final String name;
    private final Instant start;

    public RequestStart(String name) {
        this(name, Instant.now());
    }

    public RequestStart(String name, Instant start) {
        Preconditions.checkArgument(StringUtils.isNotBlank(name));
        this.name = name;
        this.start = Preconditions.checkNotNull(start);
    }

    public String getName() {
        return name;
    }

    public Instant getStart() {
        return start;
    }
}
