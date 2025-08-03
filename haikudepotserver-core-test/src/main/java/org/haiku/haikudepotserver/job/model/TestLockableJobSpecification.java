/*
 * Copyright 2016-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

import java.util.UUID;

public class TestLockableJobSpecification extends AbstractJobSpecification {

    private String name;
    private UUID lockId = UUID.randomUUID();

    public TestLockableJobSpecification() {
        super();
    }

    public TestLockableJobSpecification(String name) {
        super();
        this.name = name;
    }

    public UUID getLockId() {
        return lockId;
    }

    public void setLockId(UUID lockId) {
        this.lockId = lockId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

}
