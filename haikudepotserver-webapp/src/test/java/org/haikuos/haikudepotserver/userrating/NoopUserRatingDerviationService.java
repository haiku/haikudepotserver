/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import org.haikuos.haikudepotserver.userrating.model.UserRatingDerivationJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NoopUserRatingDerviationService implements UserRatingDerivationService {

    protected static Logger logger = LoggerFactory.getLogger(NoopUserRatingDerviationService.class);

    @Override
    public void submit(UserRatingDerivationJob job) {
        logger.info("did submit request to derive user rating for pkg; {} -- will ignore (noop)",job.getPkgName());
    }
}
