/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.Preconditions;
import org.haikuos.haikudepotserver.support.AbstractLocalBackgroundProcessingService;
import org.haikuos.haikudepotserver.userrating.model.UserRatingDerivationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

/**
 * <p>User ratings are collected from users.  User ratings can then be used to derive a overall or 'aggregated'
 * rating for a package.  This class is about deriving those composite ratings.  Because they make take a little
 * while to work out, they are not done in real time.  Instead they are handed asynchronously in the background.
 * This service also offers the function of making these derivations in the background.</p>
 */

// TODO; at some time in the future, when it is worthwhile, use a JMS broker to not loose jobs.

public class UserRatingDerivationService extends AbstractLocalBackgroundProcessingService {

    protected static Logger logger = LoggerFactory.getLogger(UserRatingDerivationService.class);

    @Resource
    UserRatingOrchestrationService userRatingOrchestrationService;

    public void submit(final UserRatingDerivationJob job) {
        Preconditions.checkNotNull(job);
        Preconditions.checkState(null!=executor, "the service is not running, but a job is being submitted");
        executor.submit(new UserRatingDerivationJobRunnable(this, job));
        logger.info("have submitted job to derive user rating; {}", job.toString());
    }

    protected void run(UserRatingDerivationJob job) {
        Preconditions.checkNotNull(job);
        userRatingOrchestrationService.updateUserRatingDerivation(job.getPkgName());
    }

    /**
     * <p>This is the object that gets enqueued to actually do the work.</p>
     */

    public static class UserRatingDerivationJobRunnable implements Runnable {

        private UserRatingDerivationJob job;

        private UserRatingDerivationService service;

        public UserRatingDerivationJobRunnable(
                UserRatingDerivationService service,
                UserRatingDerivationJob job) {
            Preconditions.checkNotNull(service);
            Preconditions.checkNotNull(job);
            this.service = service;
            this.job = job;
        }

        public UserRatingDerivationJob getJob() {
            return job;
        }

        @Override
        public void run() {
            service.run(job);
        }

    }

}
