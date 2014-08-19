package org.haikuos.haikudepotserver.userrating;

import com.google.common.base.Preconditions;
import org.haikuos.haikudepotserver.support.AbstractLocalBackgroundProcessingService;
import org.haikuos.haikudepotserver.userrating.model.UserRatingDerivationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Resource;

/**
 * <p>This implementation of the {@link org.haikuos.haikudepotserver.userrating.UserRatingDerivationService}
 * operates in the same runtime as the application and has no persistence or distributed behaviour.</p>
 */

public class LocalUserRatingDerivationService
        extends AbstractLocalBackgroundProcessingService
        implements UserRatingDerivationService {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingDerivationService.class);

    @Resource
    UserRatingOrchestrationService userRatingOrchestrationService;

    public void submit(final UserRatingDerivationJob job) {
        Preconditions.checkNotNull(job);
        Preconditions.checkState(null!=executor, "the service is not running, but a job is being submitted");
        executor.submit(new UserRatingDerivationJobRunnable(this, job));
        LOGGER.info("have submitted job to derive user rating; {}", job.toString());
    }

    protected void run(UserRatingDerivationJob job) {
        Preconditions.checkNotNull(job);

        if(job.appliesToAllPkgs()) {
            userRatingOrchestrationService.updateUserRatingDerivationsForAllPkgs();
        }
        else {
            userRatingOrchestrationService.updateUserRatingDerivation(job.getPkgName());
        }
    }

    /**
     * <p>This is the object that gets enqueued to actually do the work.</p>
     */

    public static class UserRatingDerivationJobRunnable implements Runnable {

        private UserRatingDerivationJob job;

        private LocalUserRatingDerivationService service;

        public UserRatingDerivationJobRunnable(
                LocalUserRatingDerivationService service,
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
