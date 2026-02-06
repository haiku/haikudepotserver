/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.TestLockableJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * <p>This is a job that after starting to run will be able to coordinate progress
 * with the test logic. It is used in specific situations when the test code should
 * check behaviour around jobs when there is actually a job running.</p>
 */

@Component
public class TestLockableJobRunner extends AbstractJobRunner<TestLockableJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(TestNumberedLinesJobRunner.class);

    private final Lock locksLock = new ReentrantLock();
    private final Map<UUID, Lock> locks = new HashMap<>();

    @Override
    public Class<TestLockableJobSpecification> getSupportedSpecificationClass() {
        return TestLockableJobSpecification.class;
    }

    @Override
    public void run(JobService jobService, TestLockableJobSpecification specification) throws IOException, JobRunnerException {
        Lock lock = getLock(specification.getLockId());
        String name = specification.getName();

        if (null != lock) {
            LOGGER.info("will lock [{}]", name);
            lock.lock();
            LOGGER.info("will unlock [{}]", name);
            lock.unlock();
            LOGGER.info("did unlock [{}]", name);
        } else {
            LOGGER.info("no locking [{}]", name);
        }
    }

    public Lock getLock(UUID lockId) {
        locksLock.lock();

        try {
            return locks.computeIfAbsent(
                    lockId,
                    (_) -> new ReentrantLock()
            );
        } finally {
            locksLock.unlock();
        }
    }

}
