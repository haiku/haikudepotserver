/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job.model;

import java.util.concurrent.locks.Lock;

public class TestLockableJobSpecification extends AbstractJobSpecification {

    private Lock lock;

    public TestLockableJobSpecification() {
        this(null);
    }

    public TestLockableJobSpecification(Lock lock) {
        this.lock = lock;
    }

    public Lock getLock() {
        return lock;
    }

}
