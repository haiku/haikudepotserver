/*
 * Copyright 2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */
package org.haiku.haikudepotserver.storage.model;

import org.haiku.haikudepotserver.job.model.AbstractJobSpecification;

public class DataStorageGarbageCollectionJobSpecification extends AbstractJobSpecification {

    private Long olderThanMillis;

    public Long getOlderThanMillis() {
        return olderThanMillis;
    }

    public void setOlderThanMillis(Long olderThanMillis) {
        this.olderThanMillis = olderThanMillis;
    }
}
