/*
 * Copyright 2016, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.job;

import org.haiku.haikudepotserver.job.model.JobRunnerException;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.job.model.TestLockableJobSpecification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.locks.Lock;

public class TestLockableJobRunner extends AbstractJobRunner<TestLockableJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(TestNumberedLinesJobRunner.class);

    @Override
    public void run(JobService jobService, TestLockableJobSpecification specification) throws IOException, JobRunnerException {
        Lock lock = specification.getLock();

        if (null != lock) {
            LOGGER.info("will lock");
            specification.getLock().lock();
            LOGGER.info("will unlock");
            specification.getLock().unlock();
            LOGGER.info("did unlock");
        } else {
            LOGGER.info("no locking");
        }
    }

}
