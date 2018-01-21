/*
 * Copyright 2018, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.job;

import com.google.common.base.Preconditions;
import org.haiku.haikudepotserver.job.AbstractJobRunner;
import org.haiku.haikudepotserver.job.model.JobService;
import org.haiku.haikudepotserver.userrating.model.UserRatingDerivationJobSpecification;
import org.haiku.haikudepotserver.userrating.model.UserRatingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Processes the user rating requests for a package and store them into the
 * package entity.
 */

@Component
public class UserRatingDerivationJobRunner
        extends AbstractJobRunner<UserRatingDerivationJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingDerivationJobRunner.class);

    private final UserRatingService userRatingService;

    public UserRatingDerivationJobRunner(UserRatingService userRatingService) {
        this.userRatingService = Preconditions.checkNotNull(userRatingService);
    }

    public void run(JobService jobService, UserRatingDerivationJobSpecification job) {
        Preconditions.checkNotNull(job);

        if(job.appliesToAllPkgs()) {
            userRatingService.updateUserRatingDerivationsForAllPkgs();
        }
        else {
            userRatingService.updateUserRatingDerivation(job.getPkgName());
        }
    }

}
