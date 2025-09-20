/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.support.eventing.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.haiku.haikudepotserver.job.model.JobAvailableEvent;
import org.haiku.haikudepotserver.support.cayenne.QueryCacheRemoveEvent;

/**
 * <p>Superclass of types that are an event emitted or received by HDS.</p>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "type")
@JsonSubTypes({
        @JsonSubTypes.Type(value = JobAvailableEvent.class, name = "JobAvailableEvent"),
        @JsonSubTypes.Type(value = QueryCacheRemoveEvent.class, name = "QueryCacheDropEvent")
})
public abstract class InterProcessEvent {

    private String sourceIdentifier;

    public InterProcessEvent() {
    }

    @JsonCreator
    public InterProcessEvent(
            @JsonProperty String sourceIdentifier) {
        Preconditions.checkArgument(StringUtils.isNotBlank(sourceIdentifier), "the source identifier is required");
        this.sourceIdentifier = sourceIdentifier;
    }

    public String getSourceIdentifier() {
        return sourceIdentifier;
    }

    public void setSourceIdentifier(String sourceIdentifier) {
        this.sourceIdentifier = sourceIdentifier;
    }
}
