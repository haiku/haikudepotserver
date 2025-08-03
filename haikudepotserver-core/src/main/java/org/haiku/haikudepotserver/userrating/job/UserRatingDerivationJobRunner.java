/*
 * Copyright 2018-2025, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haiku.haikudepotserver.userrating.job;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
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

    protected final static Logger LOGGER = LoggerFactory.getLogger(UserRatingDerivationJobRunner.class);

    private final UserRatingService userRatingService;

    public UserRatingDerivationJobRunner(UserRatingService userRatingService) {
        this.userRatingService = Preconditions.checkNotNull(userRatingService);
    }

    @Override
    public Class<UserRatingDerivationJobSpecification> getSupportedSpecificationClass() {
        return UserRatingDerivationJobSpecification.class;
    }

    public void run(JobService jobService, UserRatingDerivationJobSpecification job) {
        Preconditions.checkNotNull(job);

        if (StringUtils.isNotBlank(job.getUserNickname())) {
            userRatingService.updateUserRatingDerivationsForUser(job.getUserNickname());
        }
        else {
            if (StringUtils.isNotBlank(job.getPkgName())) {
                userRatingService.updateUserRatingDerivationsForPkg(job.getPkgName());
            }
            else {
                userRatingService.updateUserRatingDerivationsForAllPkgs();
            }
        }
    }

}
