/*
 * Copyright 2014, Andrew Lindesay
 * Distributed under the terms of the MIT License.
 */

package org.haikuos.haikudepotserver.userrating;

import org.haikuos.haikudepotserver.userrating.model.UserRatingDerivationJob;

/**
 * <p>User ratings are collected from users.  User ratings can then be used to derive a overall or 'aggregated'
 * rating for a package.  This class is about deriving those composite ratings.  Because they make take a little
 * while to work out, they are not done in real time.  Instead they are handed asynchronously in the background.
 * This service also offers the function of making these derivations in the background.</p>
 */

public interface UserRatingDerivationService {

    public void submit(final UserRatingDerivationJob job);

}
