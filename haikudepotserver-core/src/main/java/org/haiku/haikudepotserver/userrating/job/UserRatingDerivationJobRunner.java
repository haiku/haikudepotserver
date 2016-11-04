/*
 * Copyright 2014-2016, Andrew Lindesay
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

import javax.annotation.Resource;

public class UserRatingDerivationJobRunner
        extends AbstractJobRunner<UserRatingDerivationJobSpecification> {

    protected static Logger LOGGER = LoggerFactory.getLogger(UserRatingDerivationJobRunner.class);

    @Resource
    private UserRatingService userRatingService;

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
